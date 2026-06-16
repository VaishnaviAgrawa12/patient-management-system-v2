# Patient Management System

A production-grade, microservices-based healthcare backend built with Spring Boot. This project demonstrates real-world backend architecture including inter-service communication via gRPC and Kafka, JWT authentication, API Gateway routing, containerization with Docker, and AWS infrastructure provisioning using CDK and LocalStack.

---

## Architecture Overview

```
                        CLIENT (Postman / Frontend)
                                    |
                            API GATEWAY :4004
                         (JWT validation filter)
                        /           |            \
                       /            |             \
              Patient Service   Auth Service   (other routes)
                 :4000              :4005
                  |                  |
        patient-service-db    auth-service-db
          (PostgreSQL :5000)   (PostgreSQL :5001)
                  |
          Billing Service
          (gRPC :9001)
                  |
               Kafka :9092
                  |
          Analytics Service
               :4002
```

---

## Services

| Service | Port | Description |
|---|---|---|
| API Gateway | 4004 | Single entry point, JWT validation, request routing |
| Patient Service | 4000 | REST API for patient CRUD operations |
| Auth Service | 4005 | JWT login and token validation |
| Billing Service | 4001 / 9001 | gRPC server for billing account creation |
| Analytics Service | 4002 | Kafka consumer for patient event analytics |
| Patient Service DB | 5000 | PostgreSQL database for patient data |
| Auth Service DB | 5001 | PostgreSQL database for auth data |
| Kafka | 9092 | Event streaming between services |

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.x** — REST APIs, dependency injection, JPA
- **Spring Cloud Gateway** — API Gateway with custom JWT filter
- **PostgreSQL 16** — Relational database
- **gRPC + Protocol Buffers** — Synchronous inter-service communication
- **Apache Kafka** — Asynchronous event streaming
- **JWT (HS384)** — Stateless authentication
- **BCrypt** — Password hashing
- **Docker + Docker Compose** — Containerization
- **AWS CDK (Java)** — Infrastructure as code
- **LocalStack** — Local AWS environment for development

---

## Project Structure

```
patient-management-system-v2/
├── api-gateway/               # Spring Cloud Gateway + JWT filter
├── auth-service/              # JWT authentication service
├── patient-service/           # Patient CRUD REST API
├── billing-service/           # gRPC billing server
├── analytics-service/         # Kafka consumer
├── grpc-requests/             # Protobuf definitions
├── integration-tests/         # REST Assured integration tests
├── infrastructure/            # AWS CDK stack (LocalStack)
└── docker-compose.yml         # Full stack orchestration
```

---

## Prerequisites

- Java 21
- Docker Desktop
- Maven
- Node.js (required by AWS CDK's jsii runtime)

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/VaishnaviAgrawa12/patient-management-system-v2.git
cd patient-management-system-v2
```

### 2. Start all services

```bash
docker-compose up --build
```

This starts all 8 services in the correct dependency order with healthchecks.

### 3. Verify services are running

```bash
docker ps
```

All containers should show `Up` status. The databases will show `(healthy)`.

---

## API Usage

### Authentication

**Login and get a JWT token:**

```
POST http://localhost:4005/login
Content-Type: application/json

{
  "email": "testuser@test.com",
  "password": "password123"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9..."
}
```

### Patient Endpoints (via API Gateway)

All requests must include the JWT token as a Bearer token in the Authorization header.

```
Authorization: Bearer <your-token-here>
```

| Method | Endpoint | Description |
|---|---|---|
| GET | `http://localhost:4004/api/patients` | Get all patients |
| POST | `http://localhost:4004/api/patients` | Create a patient |
| PUT | `http://localhost:4004/api/patients/{id}` | Update a patient |
| DELETE | `http://localhost:4004/api/patients/{id}` | Delete a patient |

### Create Patient Request Body

```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "address": "123 Main St",
  "dateOfBirth": "1990-01-01"
}
```

---

## How It Works

### Request Flow Through Gateway

Every client request hits the API Gateway first:

```
1. Client sends request with Bearer token
2. JwtValidationGatewayFilterFactory extracts token
3. Gateway calls auth-service /validate
4. If 200 OK → request forwarded to patient-service
5. If 401    → request rejected immediately
```

### Patient Creation Flow

When a patient is created, two things happen automatically:

```
1. Patient saved to PostgreSQL
2. gRPC call → Billing Service creates billing account
3. Kafka event published → Analytics Service records the event
```

### JWT Authentication Flow

```
1. POST /login with credentials
2. User looked up by email in auth-service-db
3. BCrypt verifies password
4. JWT token generated with userId, email, expiry (HS384)
5. Token returned to client
```

---

## Environment Variables

Key environment variables configured in `docker-compose.yml`:

| Variable | Service | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | patient-service, auth-service | PostgreSQL connection string |
| `JWT_SECRET` | auth-service | Base64-encoded secret key |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | patient-service, analytics-service | Kafka broker address |
| `BILLING_SERVICE_ADDRESS` | patient-service | Billing service hostname |
| `BILLING_SERVICE_GRPC_PORT` | patient-service | gRPC port for billing service |
| `AUTH_SERVICE_URL` | api-gateway | Auth service URL for token validation |

> **Note:** `JWT_SECRET` must be a valid Base64-encoded string. Generate one with:
> ```bash
> echo -n "your-secret-key-here" | base64
> ```

---

## Running Individual Services

Start specific services only:

```bash
# Run only auth stack
docker-compose up auth-service api-gateway

# Run with rebuild
docker-compose up --build patient-service

# Stop everything
docker-compose down

# View logs for a specific service
docker logs auth-service -f
```

---

## Integration Tests

Integration tests use REST Assured and test the full request flow through the API Gateway.

```bash
cd integration-tests
mvn test
```

Tests cover:
- Auth service login returns valid JWT
- Protected endpoints return 401 without token
- Patient CRUD operations via gateway

---

## AWS Infrastructure (LocalStack)

The `infrastructure/` module contains an AWS CDK stack that provisions the production architecture locally using LocalStack.

**Architecture mirrors:**
- VPC with public/private subnets
- ECS Cluster with tasks for each service
- RDS instances for PostgreSQL databases
- MSK (Managed Kafka) cluster
- Application Load Balancer

### Run CDK synthesis

```bash
cd infrastructure
mvn compile exec:java
```

This generates CloudFormation templates in `cdk.out/`.

### Deploy to LocalStack

```bash
# Start LocalStack first
localstack start

# Deploy stack
cdklocal deploy
```

---

## Key Design Decisions

**Why gRPC for Billing?**
Patient-to-billing communication is synchronous — a billing account must be created when a patient is added. gRPC provides low-latency, strongly-typed communication using Protocol Buffers, making it ideal for this internal synchronous call.

**Why Kafka for Analytics?**
Analytics is a fire-and-forget concern — patient service shouldn't wait for analytics to process. Kafka decouples the services, meaning analytics service downtime doesn't affect patient creation, and events are queued for processing when it recovers.

**Why API Gateway?**
Centralizes cross-cutting concerns: authentication, routing, and (in production) rate limiting and load balancing. Only the gateway is exposed publicly — all other services remain on a private network.

---

## Production Architecture

In a real deployment on AWS, the docker-compose services map to:

| Local | AWS |
|---|---|
| docker-compose services | ECS Tasks (Fargate) |
| PostgreSQL containers | RDS (PostgreSQL) |
| Kafka container | MSK (Managed Kafka) |
| Docker network | VPC (private subnet) |
| localhost ports | Application Load Balancer |

---

## Swagger UI

Each service exposes API documentation:

```
Patient Service:  http://localhost:4000/swagger-ui/index.html
Auth Service:     http://localhost:4005/swagger-ui/index.html
```

---

## Author

**Vaishnavi Agrawal**
Technical Engineer — API Integration & Workflows

[LinkedIn](https://www.linkedin.com/in/vaishnavi-agrawal-0220591b7/) | [Medium](https://medium.com/@vaishnaviagrawal2999) | [GitHub](https://github.com/VaishnaviAgrawa12)
