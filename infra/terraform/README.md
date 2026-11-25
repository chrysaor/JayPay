# Terraform

```
infra/terraform/
  modules/   reusable building blocks, no provider configuration
  envs/      one root module per environment, each with its own state
```

`modules/dynamodb-money-sum` is the table `money-query-service` reads and writes.

Terraform runs from the official image, so nothing is installed locally:

```
tf() { docker run --rm -v "$PWD":/tf -w /tf/envs/dev -e AWS_PROFILE -v "$HOME/.aws":/root/.aws:ro hashicorp/terraform:1.17.2 "$@"; }
tf init
tf plan
```

Checks that need no credentials, also run in CI:

```
docker run --rm -v "$PWD":/tf -w /tf hashicorp/terraform:1.17.2 fmt -check -recursive
docker run --rm -v "$PWD":/tf -w /tf/envs/dev hashicorp/terraform:1.17.2 sh -c 'terraform init -backend=false && terraform validate'
```

State is local until a remote backend is chosen. DynamoDB Local cannot be a target for this module: AWS provider 6.x waits for a `WarmThroughput` field that DynamoDB Local does not return.
