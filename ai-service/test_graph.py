import pytest
from main import app_graph, AgentState

def test_approved_loan_flow():
    # Input: Good credit score, high income, standard SSN, standard address
    initial_state = {
        "loan_id": "test-loan-1",
        "user_id": "test-user-1",
        "amount": 25000.0,
        "term_months": 36,
        "credit_score": 750,
        "annual_income": 120000.0,
        "ssn": "123-45-6789",
        "address": "123 Safe St, San Francisco"
    }

    # Act
    result = app_graph.invoke(initial_state)

    # Assert
    assert result["risk_score"] == "Low"
    assert result["fraud_risk"] == "Pass"
    assert result["document_status"] == "Pass"
    assert result["final_status"] == "APPROVED"
    assert "APPROVED" in result["final_explanation"]

def test_rejected_loan_flow_due_to_credit():
    # Input: Bad credit score, good SSN, good address
    initial_state = {
        "loan_id": "test-loan-2",
        "user_id": "test-user-1",
        "amount": 50000.0,
        "term_months": 60,
        "credit_score": 550,
        "annual_income": 120000.0,
        "ssn": "123-45-6789",
        "address": "123 Safe St, San Francisco"
    }

    # Act
    result = app_graph.invoke(initial_state)

    # Assert
    assert result["risk_score"] == "High"
    assert result["final_status"] == "REJECTED"
    assert "REJECTED" in result["final_explanation"]

def test_rejected_loan_flow_due_to_fraud():
    # Input: Good credit, high income, fraudulent SSN (starts with 9)
    initial_state = {
        "loan_id": "test-loan-3",
        "user_id": "test-user-1",
        "amount": 10000.0,
        "term_months": 12,
        "credit_score": 780,
        "annual_income": 150000.0,
        "ssn": "987-65-4321",
        "address": "123 Safe St, San Francisco"
    }

    # Act
    result = app_graph.invoke(initial_state)

    # Assert
    assert result["fraud_risk"] == "Fail"
    assert result["final_status"] == "REJECTED"
    assert "REJECTED" in result["final_explanation"]

def test_under_review_due_to_address_keyword():
    # Input: Good credit, good SSN, address contains 'Metropolis'
    initial_state = {
        "loan_id": "test-loan-4",
        "user_id": "test-user-1",
        "amount": 20000.0,
        "term_months": 24,
        "credit_score": 720,
        "annual_income": 80000.0,
        "ssn": "123-45-6789",
        "address": "456 Daily Planet, Metropolis"
    }

    # Act
    result = app_graph.invoke(initial_state)

    # Assert
    assert result["document_status"] == "Flagged"
    assert result["final_status"] == "UNDER_REVIEW"
    assert "UNDER_REVIEW" in result["final_explanation"]

def test_under_review_due_to_ssn_warning():
    # Input: Good credit, SSN starts with 8
    initial_state = {
        "loan_id": "test-loan-5",
        "user_id": "test-user-1",
        "amount": 20000.0,
        "term_months": 24,
        "credit_score": 720,
        "annual_income": 80000.0,
        "ssn": "823-45-6789",
        "address": "123 Safe St, San Francisco"
    }

    # Act
    result = app_graph.invoke(initial_state)

    # Assert
    assert result["fraud_risk"] == "Warning"
    assert result["final_status"] == "UNDER_REVIEW"
    assert "UNDER_REVIEW" in result["final_explanation"]
