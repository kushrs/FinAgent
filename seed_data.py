import json
import time
import requests

def seed_data():
    gateway_url = "http://gateway-service:8080"
    try:
        requests.get(gateway_url + "/actuator/health", timeout=2)
    except Exception:
        gateway_url = "http://localhost:8080"
    print(f"=== STARTING DATA SEEDING PROCESS ON {gateway_url} ===")

    # Define User Roles
    users = [
        {"name": "Alice Green", "email": "alice@example.com", "password": "password123", "role": "CUSTOMER"},
        {"name": "Bob Miller", "email": "bob@example.com", "password": "password123", "role": "CUSTOMER"},
        {"name": "Harvey Dent", "email": "harvey@example.com", "password": "password123", "role": "CUSTOMER"},
        {"name": "Loan Officer", "email": "officer@example.com", "password": "password123", "role": "LOAN_OFFICER"},
        {"name": "Risk Analyst", "email": "analyst@example.com", "password": "password123", "role": "RISK_ANALYST"},
        {"name": "System Admin", "email": "admin@example.com", "password": "password123", "role": "ADMIN"}
    ]

    tokens = {}
    
    # 1. Register and Login Users
    for user in users:
        print(f"\nRegistering {user['role']}: {user['name']} ({user['email']})...")
        try:
            reg_res = requests.post(f"{gateway_url}/api/auth/register", json=user)
            if reg_res.status_code in (200, 201):
                print(f"-> Successfully registered.")
            else:
                print(f"-> Registration returned status {reg_res.status_code}: {reg_res.text}")
        except Exception as e:
            print(f"-> Registration failed: {e}")

        print(f"Logging in {user['email']}...")
        try:
            login_res = requests.post(f"{gateway_url}/api/auth/login", json={
                "email": user["email"],
                "password": user["password"]
            })
            login_res.raise_for_status()
            tokens[user["email"]] = login_res.json()["accessToken"]
            print("-> Successfully authenticated.")
        except Exception as e:
            print(f"-> Login failed: {e}")

    # 2. Submit Loan & Profile for Alice (Clean Profile -> Approved)
    print("\n--- Processing Loan 1: Alice Green (Clean Profile) ---")
    headers_alice = {"Authorization": f"Bearer {tokens['alice@example.com']}"}
    try:
        loan_res = requests.post(f"{gateway_url}/api/loans/apply", json={
            "amount": 25000.0,
            "termMonths": 36
        }, headers=headers_alice)
        loan_res.raise_for_status()
        print("-> Loan application created.")
        
        profile_res = requests.post(f"{gateway_url}/api/customers/profile", json={
            "ssn": "123-45-6789",
            "address": "123 Oak St, Seattle, WA",
            "annualIncome": 140000.0,
            "employmentStatus": "EMPLOYED"
        }, headers=headers_alice)
        profile_res.raise_for_status()
        print("-> Customer profile submitted. Dispatched Bureau check.")
    except Exception as e:
        print(f"-> Alice processing failed: {e}")

    # 3. Submit Loan & Profile for Bob (High-Risk Profile -> Rejected)
    print("\n--- Processing Loan 2: Bob Miller (High-Risk Profile) ---")
    headers_bob = {"Authorization": f"Bearer {tokens['bob@example.com']}"}
    try:
        loan_res = requests.post(f"{gateway_url}/api/loans/apply", json={
            "amount": 45000.0,
            "termMonths": 36
        }, headers=headers_bob)
        loan_res.raise_for_status()
        print("-> Loan application created.")
        
        profile_res = requests.post(f"{gateway_url}/api/customers/profile", json={
            "ssn": "321-65-4321",
            "address": "456 Maple Rd, Dallas, TX",
            "annualIncome": 22000.0,
            "employmentStatus": "UNEMPLOYED"
        }, headers=headers_bob)
        profile_res.raise_for_status()
        print("-> Customer profile submitted. Dispatched Bureau check.")
    except Exception as e:
        print(f"-> Bob processing failed: {e}")

    # 4. Submit Loan & Profile for Harvey Dent (Fraud/Address Warning -> Under Review)
    print("\n--- Processing Loan 3: Harvey Dent (Warning Profile) ---")
    headers_harvey = {"Authorization": f"Bearer {tokens['harvey@example.com']}"}
    try:
        loan_res = requests.post(f"{gateway_url}/api/loans/apply", json={
            "amount": 60000.0,
            "termMonths": 36
        }, headers=headers_harvey)
        loan_res.raise_for_status()
        print("-> Loan application created.")
        
        profile_res = requests.post(f"{gateway_url}/api/customers/profile", json={
            "ssn": "888-99-1111",
            "address": "789 Gotham Ave, Gotham",
            "annualIncome": 120000.0,
            "employmentStatus": "EMPLOYED"
        }, headers=headers_harvey)
        profile_res.raise_for_status()
        print("-> Customer profile submitted. Dispatched Bureau check.")
    except Exception as e:
        print(f"-> Harvey processing failed: {e}")

    # 5. Sleep to allow async processing
    print("\nSleeping 10 seconds for Kafka event flow & LangGraph execution...")
    time.sleep(10)
    print("=== SEEDING PROCESS COMPLETED SUCCESSFULY ===")

if __name__ == "__main__":
    seed_data()
