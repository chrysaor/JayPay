locals {
  schema         = jsondecode(file("${path.module}/table.json"))
  understood     = ["AttributeDefinitions", "KeySchema", "BillingMode"]
  key_attributes = { for key in local.schema.KeySchema : key.KeyType => key.AttributeName }
}

resource "aws_dynamodb_table" "this" {
  name         = var.table_name
  billing_mode = local.schema.BillingMode
  hash_key     = local.key_attributes["HASH"]
  range_key    = lookup(local.key_attributes, "RANGE", null)

  dynamic "attribute" {
    for_each = local.schema.AttributeDefinitions
    content {
      name = attribute.value.AttributeName
      type = attribute.value.AttributeType
    }
  }

  point_in_time_recovery {
    enabled = var.point_in_time_recovery
  }

  server_side_encryption {
    enabled = true
  }

  tags = var.tags

  lifecycle {
    precondition {
      condition     = length(setsubtract(keys(local.schema), local.understood)) == 0
      error_message = "table.json sets ${join(", ", setsubtract(keys(local.schema), local.understood))}, which this module does not apply to AWS. Map it in main.tf or remove it."
    }
  }
}
