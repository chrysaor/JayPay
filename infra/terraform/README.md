# Terraform

```
infra/terraform/
  modules/   reusable building blocks, no provider configuration
  envs/      one root module per environment, each with its own state
```

`modules/dynamodb-money-sum` is the table `money-query-service` reads and writes.

## One table definition

`modules/dynamodb-money-sum/table.json` holds the key schema, the attribute definitions and the billing mode in the shape of the DynamoDB `CreateTable` request. Everything that creates the table reads it:

| Reader | Where the table goes |
| --- | --- |
| `modules/dynamodb-money-sum/main.tf` | AWS, through `terraform apply` |
| `infra/docker/dynamodb/table.sh` | DynamoDB Local in the local stack, which mounts the file |
| `DynamoDBAdapterTest` in money-query-service | DynamoDB Local in a Testcontainers container |

The table name is not in the file because it differs per environment. Terraform takes it as `table_name`, and money-query-service reads `DYNAMODB_TABLE_NAME`.

The module refuses to plan when `table.json` sets a field that `main.tf` does not map, such as `GlobalSecondaryIndexes`, so a field added for the local stack cannot silently miss AWS. `envs/dev/tests/table.tftest.hcl` plans the module with a mock provider and fails when the planned table differs from `table.json`. `make verify` runs both as part of the `terraform` step, and `make verify-selftest` plants each failure.

The module is not applied to DynamoDB Local. AWS provider 6.74.0 waits after `CreateTable` until `DescribeTable` reports a `WarmThroughput` status, and DynamoDB Local 3.3.2 never returns one, so `terraform apply` polls until it times out.

## Checks without credentials

`make verify STEPS=terraform` runs `fmt -check`, `init -backend=false`, `validate` and `test` from the official image. CI runs the same step.

## Apply to AWS, check the service, destroy

This applies `envs/dev` to a real account, proves that money-query-service reads and writes the table, and removes the table again. Replace `<profile>` with the AWS CLI profile and `<account-id>` with the 12-digit account id. Every command runs from the repository root.

The profile needs these actions on `arn:aws:dynamodb:ap-northeast-2:<account-id>:table/MoneyIncreaseEventByRegion`: `CreateTable`, `DeleteTable`, `DescribeTable`, `UpdateTable`, `DescribeContinuousBackups`, `UpdateContinuousBackups`, `DescribeTimeToLive`, `ListTagsOfResource`, `TagResource`, `UntagResource`, `GetItem`, `PutItem` and `UpdateItem`. The table bills per request. Point-in-time recovery bills per GB of table, which stays near zero for a few smoke items.

### State

State stays in the local file `infra/terraform/envs/dev/terraform.tfstate`, which git ignores. The table lives only until the destroy below, so the state never leaves the machine that applies it. Keep the file until the destroy has finished, because without it Terraform no longer knows the table exists.

An environment that stays up, or one that a GitOps controller or CI applies, needs shared state first. Create a versioned S3 bucket and add this to the `terraform` block of the environment, then run `terraform init -migrate-state`:

```hcl
backend "s3" {
  bucket       = "<state-bucket>"
  key          = "jaypay/dev/terraform.tfstate"
  region       = "ap-northeast-2"
  use_lockfile = true
}
```

`use_lockfile` locks with an object next to the state, so no DynamoDB lock table is needed. It requires Terraform 1.10, which `required_version` already demands.

### 1. Credentials

```sh
aws sts get-caller-identity --profile <profile>
eval "$(aws configure export-credentials --profile <profile> --format env)"
```

The first command must print `<account-id>`. The second exports `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and, for an SSO or assumed-role profile, `AWS_SESSION_TOKEN` into the shell. Both Terraform and money-query-service take the credentials from these variables, so neither reads `~/.aws`. Temporary credentials expire, usually after an hour. Export them again before a later step if one fails with `ExpiredToken`.

### 2. Apply

```sh
tf() {
  docker run --rm -it --user "$(id -u):$(id -g)" -e CHECKPOINT_DISABLE=1 \
    -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN \
    -v "$PWD/infra/terraform:/tf" -w /tf/envs/dev \
    hashicorp/terraform:1.17.2@sha256:6e9c1e647c31a960dc9b59e13272f9111d90010c10b717f77ffb324767660475 "$@"
}
tf init
tf plan -out=dev.tfplan
tf apply dev.tfplan
```

The plan must show `1 to add, 0 to change, 0 to destroy` for `module.money_sum.aws_dynamodb_table.this`. After the apply, check the table:

```sh
aws dynamodb describe-table --profile <profile> --region ap-northeast-2 \
  --table-name MoneyIncreaseEventByRegion \
  --query 'Table.{status:TableStatus,keys:KeySchema,billing:BillingModeSummary.BillingMode}'
aws dynamodb describe-continuous-backups --profile <profile> --region ap-northeast-2 \
  --table-name MoneyIncreaseEventByRegion \
  --query 'ContinuousBackupsDescription.PointInTimeRecoveryDescription.PointInTimeRecoveryStatus'
```

The table is `ACTIVE`, keyed by `PK` and `SK`, `PAY_PER_REQUEST`, with point-in-time recovery `ENABLED`.

### 3. Point money-query-service at the table

`infra/docker/compose.aws.yaml` gives money-query-service the exported credentials and the regional DynamoDB endpoint instead of DynamoDB Local. Every other container stays local.

```sh
docker compose -f infra/docker/compose.yaml -f infra/docker/compose.aws.yaml up -d --wait
make smoke
```

The smoke tops two members of a new region `Smoke-<run>` up through the saga and reads the region total of 14000 back through the admin console, so it passes only when the service both wrote to and read from the AWS table. Read the same item from AWS directly, with the region from the smoke line `a viewer reads the saga top-ups of region Smoke-<run>`:

```sh
aws dynamodb get-item --profile <profile> --region ap-northeast-2 \
  --table-name MoneyIncreaseEventByRegion \
  --key '{"PK":{"S":"Smoke-<run>"},"SK":{"S":"-1"}}'
```

The item has `"balance": {"N": "14000"}`. Then return the stack to DynamoDB Local:

```sh
docker compose -f infra/docker/compose.yaml -f infra/docker/compose.aws.yaml down
make up
```

### 4. Destroy

```sh
tf plan -destroy -out=destroy.tfplan
tf apply destroy.tfplan
aws dynamodb describe-table --profile <profile> --region ap-northeast-2 --table-name MoneyIncreaseEventByRegion
```

The plan must show `0 to add, 0 to change, 1 to destroy`, and the last command must fail with `ResourceNotFoundException`. Deleting the table also ends its point-in-time recovery. `tf state list` then prints nothing, and the state file and both plan files can be deleted.
