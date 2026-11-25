mock_provider "aws" {}

variables {
  schema = jsondecode(file("../../modules/dynamodb-money-sum/table.json"))
}

run "plans_the_table_that_table_json_describes" {
  command = plan

  module {
    source = "../../modules/dynamodb-money-sum"
  }

  assert {
    condition = {
      AttributeDefinitions = toset([for attribute in aws_dynamodb_table.this.attribute : { AttributeName = attribute.name, AttributeType = attribute.type }])
      KeySchema            = toset([for type, name in { HASH = aws_dynamodb_table.this.hash_key, RANGE = aws_dynamodb_table.this.range_key } : { AttributeName = name, KeyType = type } if name != null])
      BillingMode          = aws_dynamodb_table.this.billing_mode
      } == {
      AttributeDefinitions = toset(var.schema.AttributeDefinitions)
      KeySchema            = toset(var.schema.KeySchema)
      BillingMode          = var.schema.BillingMode
    }
    error_message = "The table Terraform plans differs from table.json."
  }
}
