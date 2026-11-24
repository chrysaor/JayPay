This folder keeps the JayPay documents from before the modernization. They describe the 2024 layout and are not maintained.

This page is the root `README.md` as of commit `7d733aa`, when the services ran on Java 11, MySQL and Axon Framework 4.6.0. For the current layout, read the [root README](../../README.md). The folder also keeps the [money service domain model](money_service/money_domain_model.png), which no page linked.

# JayPay Project Overview
![Overall Architecture](architecture/jaypay_overall_architecture.png)

```
It consists of six services: Membership, Banking, Money, Remittance, Payment, and Settlement, each configured as an independent project.
Each service implemented basic functionalities using Hexagonal Architecture, and then incorporates features such as EDA, CQRS, Event Sourcing, and the Saga Pattern.

The projects are primarily based on Spring Boot and Java 11, and the Axon Framework v4.6.0 is used to implement EDA.
```

## Using Stack in this project
- Java 11
- Spring Boot, Spring Data JPA
- Lombok
- Gradle
- JWT
- Axon Framework
- Docker, Docker Compose
- Mysql, H2
- AWS DynamoDB

## Sequence Diagram Examples

### Membership service
![Membership Sequence Example](sequences/1_membership.png)

### Banking service
![Banking Sequence Example](sequences/2_transfer_money.png)

### Money service
![Money Sequence Example](sequences/3_charge_money.png)

### Remittance service
![Remittance Sequence Example](sequences/4_remittance.png)

### Payment service
![Payment Sequence Example](sequences/5_payment.png)

### Settlement service
![Settlement Sequence Example](sequences/6_settlement.png)

## Execution
```
./gradlew docker
docker-compose up -d
```

## Service Endpoint & Swagger UI
- Membership Service
  - http://localhost:8081/memberships
  - http://localhost:8081/swagger-ui.html
- Banking Service
  - http://localhost:8082/banking
  - http://localhost:8082/swagger-ui.html
- Money Service
  - http://localhost:8083/money
  - http://localhost:8083/swagger-ui.html
- Remittance Service
  - http://localhost:8084/remittance
  - http://localhost:8084/swagger-ui.html
- Payment Service
  - http://localhost:8085/payment
  - http://localhost:8085/swagger-ui.html
- Money Aggregation Service
  - http://localhost:8086/money/aggregation
  - http://localhost:8086/swagger-ui.html
- Money Query Service
  - http://localhost:8087/money/query
  - http://localhost:8087/swagger-ui.html
- Settlement Service
  - http://localhost:8088/settlement
  - http://localhost:8088/swagger-ui.html  
- Mysql
  - http://localhost:3306
  - root password: rootpassword
  - database: jaypay
  - User/PW : mysqluser / mysqlpw
- Kafka UI
  - http://localhost:8989
- Axon Server Dashboard
  - http://localhost:8024

## Trouble Shooting
### 1. It needs connection information to use dynamo db.
[MoneyQueryService: DynamoDBAdapter.java]
```
private static final String ACCESS_KEY = "";
private static final String SECRET_KEY = "";
```
