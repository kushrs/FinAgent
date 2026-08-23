import json
import logging
import os
import threading
from typing import Dict, Any, TypedDict
from contextlib import asynccontextmanager

from fastapi import FastAPI, Response
from pydantic import BaseModel

requests_processed_count = 0
from confluent_kafka import Consumer, Producer, KafkaError

from langgraph.graph import StateGraph, START, END

# Setup logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("ai-service")

# Define AgentState
class AgentState(TypedDict):
    loan_id: str
    user_id: str
    amount: float
    term_months: int
    credit_score: int
    annual_income: float
    ssn: str
    address: str
    risk_score: str       # Low, Medium, High
    risk_reason: str
    fraud_risk: str       # Pass, Warning, Fail
    fraud_reason: str
    document_status: str  # Pass, Flagged
    document_reason: str
    final_status: str     # APPROVED, REJECTED, UNDER_REVIEW
    final_explanation: str

# Define Node: Risk Assessment Node
def risk_node(state: AgentState) -> Dict[str, Any]:
    logger.info("Executing Risk Node for loan %s", state.get("loan_id"))
    credit_score = state.get("credit_score", 0)
    annual_income = state.get("annual_income", 0.0)
    
    if credit_score < 600 or annual_income < 30000:
        risk_score = "High"
        risk_reason = f"High risk: Low credit score ({credit_score}) or insufficient income ({annual_income})."
    elif credit_score >= 700 and annual_income >= 50000:
        risk_score = "Low"
        risk_reason = f"Low risk: Strong credit score ({credit_score}) and healthy income profile ({annual_income})."
    else:
        risk_score = "Medium"
        risk_reason = f"Medium risk: Moderate credit score ({credit_score}) and steady income ({annual_income})."
        
    return {
        "risk_score": risk_score,
        "risk_reason": risk_reason
    }

# Define Node: Fraud Detection Node
def fraud_node(state: AgentState) -> Dict[str, Any]:
    logger.info("Executing Fraud Node for loan %s", state.get("loan_id"))
    ssn = state.get("ssn", "")
    
    if ssn.startswith("9"):
        fraud_risk = "Fail"
        fraud_reason = "Fraud alert: SSN starts with a flagged leading digit (9)."
    elif ssn.startswith("8"):
        fraud_risk = "Warning"
        fraud_reason = "Fraud warning: SSN starts with a warning leading digit (8)."
    else:
        fraud_risk = "Pass"
        fraud_reason = "Fraud checks passed: SSN pattern appears valid."
        
    return {
        "fraud_risk": fraud_risk,
        "fraud_reason": fraud_reason
    }

# Define Node: Document Verification Node
def document_node(state: AgentState) -> Dict[str, Any]:
    logger.info("Executing Document Node for loan %s", state.get("loan_id"))
    address = state.get("address", "")
    address_lower = address.lower()
    
    if "metropolis" in address_lower or "gotham" in address_lower:
        document_status = "Flagged"
        document_reason = f"Document verification warning: Address contains fictional city keywords ('{address}')."
    else:
        document_status = "Pass"
        document_reason = "Document verification passed: Address is formatted and verified."
        
    return {
        "document_status": document_status,
        "document_reason": document_reason
    }

# Define Node: Supervisor Node
def supervisor_node(state: AgentState) -> Dict[str, Any]:
    logger.info("Executing Supervisor Node for loan %s", state.get("loan_id"))
    risk_score = state.get("risk_score")
    risk_reason = state.get("risk_reason")
    fraud_risk = state.get("fraud_risk")
    fraud_reason = state.get("fraud_reason")
    document_status = state.get("document_status")
    document_reason = state.get("document_reason")
    
    if fraud_risk == "Fail" or risk_score == "High":
        final_status = "REJECTED"
        final_explanation = f"Loan application REJECTED. Primary factors: Risk Node rated {risk_score} ({risk_reason}) and Fraud Node rated {fraud_risk} ({fraud_reason})."
    elif fraud_risk == "Warning" or document_status == "Flagged" or risk_score == "Medium":
        final_status = "UNDER_REVIEW"
        final_explanation = f"Loan application flagged for manual UNDER_REVIEW. Factors: Risk is {risk_score} ({risk_reason}), Fraud is {fraud_risk} ({fraud_reason}), and Address validation is {document_status} ({document_reason})."
    else:
        final_status = "APPROVED"
        final_explanation = f"Loan application APPROVED. Factors: Safe indicators from Risk Node ({risk_reason}), Fraud Node ({fraud_reason}), and Document Node ({document_reason})."
        
    return {
        "final_status": final_status,
        "final_explanation": final_explanation
    }

# Build LangGraph State Machine
workflow = StateGraph(AgentState)
workflow.add_node("risk", risk_node)
workflow.add_node("fraud", fraud_node)
workflow.add_node("document", document_node)
workflow.add_node("supervisor", supervisor_node)

workflow.add_conditional_edges(
    START,
    lambda state: ["risk", "fraud", "document"],
    {
        "risk": "risk",
        "fraud": "fraud",
        "document": "document"
    }
)
workflow.add_edge("risk", "supervisor")
workflow.add_edge("fraud", "supervisor")
workflow.add_edge("document", "supervisor")
workflow.add_edge("supervisor", END)

app_graph = workflow.compile()

# Kafka Background Worker Logic
kafka_running = True

def kafka_worker_loop():
    logger.info("Starting background Kafka consumer/producer thread loop...")
    bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    
    consumer_conf = {
        'bootstrap.servers': bootstrap_servers,
        'group.id': 'ai-service-group',
        'auto.offset.reset': 'earliest',
        'enable.auto.commit': False
    }
    
    producer_conf = {
        'bootstrap.servers': bootstrap_servers
    }
    
    consumer = None
    producer = None
    
    while kafka_running:
        try:
            if consumer is None:
                consumer = Consumer(consumer_conf)
                consumer.subscribe(['loan.submitted'])
                logger.info("Successfully subscribed to Kafka topic: loan.submitted")
            
            if producer is None:
                producer = Producer(producer_conf)
                logger.info("Successfully initialized Kafka producer")
                
            msg = consumer.poll(1.0)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                else:
                    logger.error("Kafka consumer error: %s", msg.error())
                    consumer.close()
                    consumer = None
                    continue
            
            # Process received message
            try:
                payload = json.loads(msg.value().decode('utf-8'))
                logger.info("Consuming loan.submitted event for loan: %s", payload.get("loanId"))
                
                initial_state: AgentState = {
                    "loan_id": payload.get("loanId"),
                    "user_id": payload.get("userId"),
                    "amount": float(payload.get("amount", 0.0)),
                    "term_months": int(payload.get("termMonths", 0)),
                    "credit_score": int(payload.get("creditScore", 0)),
                    "annual_income": float(payload.get("annualIncome", 0.0)),
                    "ssn": payload.get("ssn", ""),
                    "address": payload.get("address", "")
                }
                
                # Execute LangGraph Workflow
                logger.info("Triggering LangGraph workflow for loan: %s", initial_state["loan_id"])
                global requests_processed_count
                requests_processed_count += 1
                result = app_graph.invoke(initial_state)
                
                # Produce outcome message
                decision_payload = {
                    "loanId": result["loan_id"],
                    "userId": result["user_id"],
                    "amount": result.get("amount", 0.0),
                    "status": result["final_status"],
                    "riskScore": result["risk_score"],
                    "fraudRisk": result["fraud_risk"],
                    "documentStatus": result["document_status"],
                    "explanation": result["final_explanation"]
                }
                
                logger.info("LangGraph execution finished. Publishing decision for loan: %s (Status: %s)", 
                            result["loan_id"], result["final_status"])
                
                producer.produce(
                    "loan.decision.made",
                    key=result["loan_id"],
                    value=json.dumps(decision_payload).encode('utf-8')
                )
                producer.flush()
                
                # Commit Kafka offsets
                consumer.commit(msg, asynchronous=False)
                
            except Exception as e:
                logger.error("Error processing loan application event: %s", e, exc_info=True)
                
        except Exception as ex:
            logger.error("Exception in Kafka worker loop: %s. Retrying in 5 seconds...", ex)
            if consumer:
                try:
                    consumer.close()
                except Exception:
                    pass
                consumer = None
            if producer:
                producer = None
            threading.Event().wait(5)
            
    # Cleanup on shutdown
    if consumer:
        try:
            consumer.close()
        except Exception:
            pass
    logger.info("Kafka worker loop shut down.")

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Start Kafka consumer thread
    worker_thread = threading.Thread(target=kafka_worker_loop, daemon=True)
    worker_thread.start()
    yield
    # Shutdown
    global kafka_running
    kafka_running = False

app = FastAPI(title="FinAgent OS AI Service", version="1.0.0", lifespan=lifespan)

# Setup Lifespan properly
# Wait, typo in lifespan=lifpan. It should be lifespan=lifespan! Let's fix that below.

class HealthResponse(BaseModel):
    status: str

@app.get("/health", response_model=HealthResponse)
def health_check():
    return HealthResponse(status="ok")

# Chat Request Schema
class ChatRequest(BaseModel):
    message: str
    history: list = []

class ChatResponse(BaseModel):
    response: str

@app.post("/chat", response_model=ChatResponse)
def chat_support(req: ChatRequest):
    msg = req.message.lower()
    logger.info("AI customer support chat input: %s", req.message)
    
    if "interest" in msg or "rate" in msg:
        res = "FinAgent OS offers highly competitive interest rates starting at 4.25% APR for low-risk, prime tier applicants (credit score > 740). Medium-risk tier applicants are evaluated at 5.5% - 7.5% APR."
    elif "status" in msg or "application" in msg:
        res = "You can view your active loan application timeline directly on the Mission Control dashboard. Once submitted, it automatically cycles through our Risk, Fraud, and OCR Document verification nodes before final supervisor decisioning."
    elif "document" in msg or "pdf" in msg or "upload" in msg:
        res = "We accept standard utility bills, tax returns, and government-issued profile documents. Please upload clear, uncropped PDF or image documents during step 3 of the loan application flow."
    elif "credit" in msg or "score" in msg:
        res = "Our AI system dynamically queries the Credit Bureau profile database upon application submission. Scores above 600 generally pass initial automated risk checks. If your score is lower, our supervisor agent may route the application for manual officer review."
    else:
        res = "Hello! I am your FinAgent OS AI support assistant. I can help answer queries about our automated loan underwriting pipeline, interest rates, document verification criteria, or check your credit health profile."
        
    return ChatResponse(response=res)

# Financial Health Request Schema
class AdvisorRequest(BaseModel):
    userId: str
    creditScore: int
    annualIncome: float
    totalDebt: float = 15000.0

class AdvisorResponse(BaseModel):
    financialHealthScore: int
    recommendations: list[str]

@app.post("/advisor/financial-health", response_model=AdvisorResponse)
def financial_advisor(req: AdvisorRequest):
    logger.info("AI financial advisor scoring for user: %s", req.userId)
    
    # Calculate simple health index [0-100]
    score = int((req.creditScore / 850.0 * 50) + (min(req.annualIncome / 150000.0, 1.0) * 50))
    score = max(min(score, 100), 10)
    
    recs = []
    if req.creditScore < 650:
        recs.append("Credit Building: Prioritize on-time payments and verify your profile with the Credit Bureau to raise your score above 700.")
    else:
        recs.append("Credit Maintenance: Excellent credit standing. Keep utilization below 10% to sustain prime status.")
        
    debt_to_income = req.totalDebt / (req.annualIncome + 1.0)
    if debt_to_income > 0.4:
        recs.append("Debt Reduction: Your debt-to-income ratio is high. Consider structured consolidation options to improve loan eligibility.")
    else:
        recs.append("Savings Buffer: Healthy debt-to-income profile. We recommend routing 20% of your annual income into high-yield accounts.")
        
    recs.append("Automated Budgeting: Connect your accounts to trigger micro-saving rules and automatically auto-pay utility statements.")
    
    return AdvisorResponse(financialHealthScore=score, recommendations=recs)

# Portfolio Monitoring Flags Schema
class RiskFlag(BaseModel):
    customerId: str
    customerName: str
    riskType: str
    description: str
    severity: str

@app.get("/portfolio/flags", response_model=list[RiskFlag])
def portfolio_flags():
    logger.info("Fetching portfolio monitoring risk alerts...")
    return [
        RiskFlag(
            customerId="cust_a9e8b7",
            customerName="Bruce Wayne",
            riskType="Suspicious Address Verification",
            description="Address registered in Gotham City. Cross-reference shows high geographical risk keyword matching.",
            severity="WARNING"
        ),
        RiskFlag(
            customerId="cust_b2d5c8",
            customerName="Clark Kent",
            riskType="Income Dissonance Warning",
            description="Annual income claim of $120,000 conflicts with local newspaper salary averages.",
            severity="INFO"
        ),
        RiskFlag(
            customerId="cust_0e3f2a",
            customerName="Harvey Dent",
            riskType="Identity Discrepancy Alert",
            description="SSN leading digits triggered fraud alarm pattern checks. Double-check profile metadata.",
            severity="CRITICAL"
        )
    ]

@app.get("/metrics")
def metrics():
    global requests_processed_count
    metrics_str = (
        "# HELP ai_requests_total Total number of AI applications evaluated\n"
        "# TYPE ai_requests_total counter\n"
        f"ai_requests_total {requests_processed_count}\n"
    )
    return Response(content=metrics_str, media_type="text/plain")

