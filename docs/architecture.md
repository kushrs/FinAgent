# FinAgent OS Architecture

FinAgent OS is an agentic AI financial platform structured as a distributed microservices monorepo. It orchestrates real-time loan underwriting, fraud validation, OCR document analysis, and portfolio health monitoring.

## Services Architecture

- **gateway-service (Port 8080)**: Central edge routing, JWT validation, and Redis-backed rate limiting.
- **auth-service (Port 8081)**: Authentication server managing RBAC and user identities.
- **customer-service (Port 8082)**: Manages customer credit scores, financial profiles, and bureau lookups.
- **loan-service (Port 8083)**: Core loan application workflows and state changes.
- **notification-service (Port 8084)**: Websocket/SSE real-time events push to frontend and user emails.
- **audit-service (Port 8085)**: Immutably tracks system actions, officer overrides, and security events.
- **analytics-service (Port 8086)**: Aggregates performance, processing latency, and volumes.
- **ai-service (Port 8000)**: LangGraph agent brain coordinating Risk, Fraud, Document, and Supervisor nodes.

## Event Pipeline (Kafka)

```
[Loan Application Submitted] (loan-service)
            │
            ▼ (topic: loan.submitted)
[LangGraph AI Agents Orchestrator] (ai-service)
    ├── Risk Assessment Agent
    ├── Fraud Detection Agent
    └── Document Verification Agent
            │
            ▼ (node inputs merged)
    ├── Supervisor Agent (synthesizes recommendation)
            │
            ▼ (topic: loan.decision.made)
[Loan Evaluation & Notification Relay] (loan-service, notification-service)
```
