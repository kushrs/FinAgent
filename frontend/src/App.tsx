import React, { useState, useEffect, useRef } from 'react';
import { 
  Activity, ShieldAlert, Cpu, Database, 
  Terminal, ArrowRight, CheckCircle2, AlertTriangle, 
  Clock, RefreshCw, BarChart3, Users, FileText, 
  MessageSquare, User, Briefcase, DollarSign, 
  TrendingUp, Award, AlertOctagon, Heart, Send, CheckCircle
} from 'lucide-react';
import axios from 'axios';

interface EventLog {
  id: string;
  timestamp: string;
  source: string;
  type: string;
  message: string;
  status: 'info' | 'success' | 'warning' | 'error';
}

interface AgentState {
  id: string;
  name: string;
  role: string;
  status: 'idle' | 'processing' | 'online';
  latency: string;
  load: number;
  accuracy: string;
}

interface LoanApplication {
  id?: string;
  amount: number;
  termMonths: number;
  ssn: string;
  address: string;
  annualIncome: number;
  employmentStatus: string;
  status?: string;
  creditScoreSnapshot?: number | null;
  explanation?: string | null;
}

interface ChatMessage {
  sender: 'user' | 'ai';
  text: string;
}

export default function App() {
  // Roles supported: CUSTOMER, LOAN_OFFICER, RISK_ANALYST, ADMIN
  const [currentRole, setCurrentRole] = useState<'CUSTOMER' | 'LOAN_OFFICER' | 'RISK_ANALYST' | 'ADMIN'>('CUSTOMER');
  
  // Navigation / Tabs
  const [customerTab, setCustomerTab] = useState<'apply' | 'twin' | 'health' | 'chat'>('apply');
  const [officerTab, setOfficerTab] = useState<'queue' | 'audit'>('queue');
  const [analystTab, setAnalystTab] = useState<'portfolio' | 'agents'>('portfolio');

  // Authentication simulator
  const [isRegistered, setIsRegistered] = useState(true);
  const [email, setEmail] = useState('borrower_demo@example.com');
  const [password, setPassword] = useState('password123');
  const [fullName, setFullName] = useState('Demo Borrower');
  const [jwtToken, setJwtToken] = useState<string | null>(null);
  const [userId, setUserId] = useState<string | null>(null);
  const [authError, setAuthError] = useState<string | null>(null);

  // Form states
  const [loanAmount, setLoanAmount] = useState<number>(55000);
  const [termMonths, setTermMonths] = useState<number>(36);
  const [ssn, setSsn] = useState<string>('888-22-1111');
  const [address, setAddress] = useState<string>('456 Daily Planet Lane, Metropolis');
  const [annualIncome, setAnnualIncome] = useState<number>(120000);
  const [employmentStatus, setEmploymentStatus] = useState<string>('EMPLOYED');

  // State data
  const [activeLoan, setActiveLoan] = useState<LoanApplication | null>(null);
  const [loansQueue, setLoansQueue] = useState<LoanApplication[]>([]);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [sseLogs, setSseLogs] = useState<EventLog[]>([
    { id: '1', timestamp: '12:00:01', source: 'GATEWAY', type: 'Gateway Ingress', message: 'Spring Cloud Gateway active on port 8080', status: 'info' },
    { id: '2', timestamp: '12:00:03', source: 'REDIS', type: 'Cache Check', message: 'Rate limiter Redis cluster connected', status: 'success' },
    { id: '3', timestamp: '12:00:05', source: 'KAFKA', type: 'Event Bus', message: 'Subscribed to topic loan.decision.made', status: 'info' },
  ]);
  const [metrics, setMetrics] = useState({
    totalUsersRegistered: 1,
    totalLoansApplied: 1,
    totalLoanAmount: 55000.0,
    approvedRate: 0.0,
    rejectedRate: 1.0,
    underReviewRate: 0.0
  });

  // Chat agent
  const [chatInput, setChatInput] = useState('');
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    { sender: 'ai', text: 'Hello! I am your FinAgent OS AI Assistant. Ask me anything about interest rates, criteria, or your application status.' }
  ]);

  // Advisor & Portfolio
  const [advisorScore, setAdvisorScore] = useState<number>(75);
  const [advisorRecs, setAdvisorRecs] = useState<string[]>([
    'Savings Buffer: Healthy debt ratio. We recommend allocating 20% of net monthly income into high-yield accounts.',
    'Credit Maintenance: Prime status tier. Keep credit card utilization below 10% to secure lowest rate offers.'
  ]);
  const [riskFlags, setRiskFlags] = useState<any[]>([]);

  // Agents static status dashboard
  const agents: AgentState[] = [
    { id: '1', name: 'Risk Assessment Agent', role: 'Risk & Affordability Evaluation', status: 'online', latency: '120ms', load: 8, accuracy: '98.4%' },
    { id: '2', name: 'Fraud Detection Agent', role: 'Identity & PII Scan anomalies', status: 'online', latency: '180ms', load: 12, accuracy: '99.1%' },
    { id: '3', name: 'Document Verification Agent', role: 'OCR Address & Profile Match', status: 'online', latency: '450ms', load: 5, accuracy: '95.8%' },
    { id: '4', name: 'Supervisor Agent', role: 'Final Multi-Agent Coordinator', status: 'online', latency: '95ms', load: 15, accuracy: '99.5%' },
    { id: '5', name: 'Portfolio Risk Agent', role: 'Real-time account drift monitor', status: 'online', latency: '210ms', load: 4, accuracy: '96.2%' },
    { id: '6', name: 'Financial Advisor Agent', role: 'Credit health & budget advisor', status: 'online', latency: '150ms', load: 3, accuracy: '97.5%' }
  ];

  const sseSourceRef = useRef<EventSource | null>(null);

  // Poll Metrics and Risk Flags
  useEffect(() => {
    fetchMetrics();
    fetchRiskFlags();
    const interval = setInterval(() => {
      fetchMetrics();
      fetchRiskFlags();
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  // Set up SSE subscription upon successful login
  useEffect(() => {
    if (jwtToken && userId) {
      subscribeToSSE();
    } else {
      if (sseSourceRef.current) {
        sseSourceRef.current.close();
        sseSourceRef.current = null;
      }
    }
    return () => {
      if (sseSourceRef.current) {
        sseSourceRef.current.close();
      }
    };
  }, [jwtToken, userId]);

  // Auto-populate credentials based on role selection
  useEffect(() => {
    if (currentRole === 'CUSTOMER') {
      setEmail('alice@example.com');
      setFullName('Alice Green');
    } else if (currentRole === 'LOAN_OFFICER') {
      setEmail('officer@example.com');
      setFullName('Loan Officer');
    } else if (currentRole === 'RISK_ANALYST') {
      setEmail('analyst@example.com');
      setFullName('Risk Analyst');
    } else if (currentRole === 'ADMIN') {
      setEmail('admin@example.com');
      setFullName('System Admin');
    }
    setPassword('password123');
  }, [currentRole]);

  const subscribeToSSE = () => {
    if (sseSourceRef.current) return;
    
    console.log("Connecting to SSE event stream...");
    const source = new EventSource("http://localhost:8080/api/notifications/stream?userId=" + userId);
    sseSourceRef.current = source;

    source.onopen = () => {
      const initLog: EventLog = {
        id: Math.random().toString(),
        timestamp: new Date().toTimeString().split(' ')[0],
        source: 'SSE_STREAM',
        type: 'Connected',
        message: 'Established live Server-Sent Events relay connection.',
        status: 'success'
      };
      setSseLogs(prev => [initLog, ...prev]);
    };

    source.addEventListener("LOAN_DECISION", (e: any) => {
      try {
        const payload = JSON.parse(e.data);
        console.log("Received SSE Loan Decision:", payload);
        
        const decisionLog: EventLog = {
          id: Math.random().toString(),
          timestamp: new Date().toTimeString().split(' ')[0],
          source: 'AI_SUPERVISOR',
          type: 'Decision Made',
          message: `Loan ID ${payload.loanId} evaluated status to ${payload.status}. Reason: ${payload.explanation}`,
          status: payload.status === 'APPROVED' ? 'success' : payload.status === 'REJECTED' ? 'error' : 'warning'
        };

        setSseLogs(prev => [decisionLog, ...prev]);
        
        // If it matches our active loan, update it
        if (activeLoan && activeLoan.id === payload.loanId) {
          setActiveLoan(prev => prev ? { ...prev, status: payload.status, explanation: payload.explanation } : null);
        }
        
        // Refresh queues and metrics
        fetchMetrics();
        if (currentRole === 'LOAN_OFFICER') {
          fetchReviewQueue();
        }
      } catch (err) {
        console.error("Error parsing SSE loan decision:", err);
      }
    });

    source.onerror = (err) => {
      console.warn("SSE connection error. Retrying...", err);
      source.close();
      sseSourceRef.current = null;
      setTimeout(subscribeToSSE, 5000);
    };
  };

  const fetchMetrics = async () => {
    try {
      const res = await axios.get("http://localhost:8080/api/analytics/metrics");
      setMetrics(res.data);
    } catch (err) {
      console.warn("Could not retrieve analytics metrics");
    }
  };

  const fetchRiskFlags = async () => {
    if (!jwtToken || currentRole !== 'RISK_ANALYST') return;
    try {
      const res = await axios.get("http://localhost:8080/api/ai/portfolio/flags", {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });
      setRiskFlags(res.data);
    } catch (err) {
      console.warn("Could not fetch portfolio risk flags");
    }
  };

  const fetchReviewQueue = async () => {
    if (!jwtToken) return;
    try {
      const res = await axios.get("http://localhost:8080/api/loans/under-review", {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });
      setLoansQueue(res.data);
    } catch (err) {
      console.warn("Failed to fetch review queue");
    }
  };

  const fetchAuditLogs = async () => {
    if (!jwtToken) return;
    try {
      const res = await axios.get("http://localhost:8080/api/audit/loans", {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });
      setAuditLogs(res.data);
    } catch (err) {
      console.warn("Failed to retrieve audit log entries");
    }
  };

  // Auth Operations
  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    try {
      await axios.post("http://localhost:8080/api/auth/register", {
        name: fullName,
        email,
        password,
        role: currentRole
      });
      setIsRegistered(true);
      alert("Registration completed. Please sign in.");
    } catch (err: any) {
      setAuthError(err.response?.data?.message || "Registration failed. Try again.");
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    try {
      const res = await axios.post("http://localhost:8080/api/auth/login", { email, password });
      setJwtToken(res.data.accessToken);
      setUserId(res.data.userId);
      
      const loginLog: EventLog = {
        id: Math.random().toString(),
        timestamp: new Date().toTimeString().split(' ')[0],
        source: 'AUTH_SERVICE',
        type: 'Authentication',
        message: `Successfully logged in as ${currentRole} (${email})`,
        status: 'success'
      };
      setSseLogs(prev => [loginLog, ...prev]);

      // Bootstrap tabs
      if (currentRole === 'LOAN_OFFICER') {
        fetchReviewQueue();
      } else if (currentRole === 'ADMIN') {
        fetchAuditLogs();
      } else if (currentRole === 'CUSTOMER') {
        // Fetch recommendations
        fetchAdvisorRecommendations(res.data.userId);
      }
    } catch (err: any) {
      setAuthError(err.response?.data?.message || "Authentication credentials rejected.");
    }
  };

  const handleLogout = () => {
    setJwtToken(null);
    setUserId(null);
    setActiveLoan(null);
    setAuditLogs([]);
    setLoansQueue([]);
    if (sseSourceRef.current) {
      sseSourceRef.current.close();
      sseSourceRef.current = null;
    }
  };

  const fetchAdvisorRecommendations = async (uid: string) => {
    if (!jwtToken) return;
    try {
      const res = await axios.post("http://localhost:8080/api/ai/advisor/financial-health", {
        userId: uid,
        creditScore: 720,
        annualIncome: 125000,
        totalDebt: 22000
      }, {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });
      setAdvisorScore(res.data.financialHealthScore);
      setAdvisorRecs(res.data.recommendations);
    } catch (err) {
      console.warn("Could not retrieve advisor recommendations");
    }
  };

  // Submit loan application
  const handleApplyLoan = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!jwtToken) return;

    try {
      // 1. Submit loan
      const loanRes = await axios.post("http://localhost:8080/api/loans/apply", {
        amount: loanAmount,
        termMonths
      }, {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });
      
      const newLoan = loanRes.data;
      setActiveLoan(newLoan);

      // 2. Submit customer profile to trigger credit fetch and AI process loop
      await axios.post("http://localhost:8080/api/customers/profile", {
        ssn,
        address,
        annualIncome,
        employmentStatus
      }, {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });

      const submitLog: EventLog = {
        id: Math.random().toString(),
        timestamp: new Date().toTimeString().split(' ')[0],
        source: 'LOAN_SERVICE',
        type: 'Application Submitted',
        message: `Registered loan application for $${loanAmount}. Triggering async bureau and LangGraph AI evaluation.`,
        status: 'success'
      };
      setSseLogs(prev => [submitLog, ...prev]);

    } catch (err: any) {
      alert("Application submission failed: " + (err.response?.data?.message || err.message));
    }
  };

  // Support chat handler
  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim() || !jwtToken) return;

    const userMsg = chatInput;
    setChatMessages(prev => [...prev, { sender: 'user', text: userMsg }]);
    setChatInput('');

    try {
      const res = await axios.post("http://localhost:8080/api/ai/chat", {
        message: userMsg
      }, {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });
      setChatMessages(prev => [...prev, { sender: 'ai', text: res.data.response }]);
    } catch (err) {
      setChatMessages(prev => [...prev, { sender: 'ai', text: "Service temporarily offline. Please check your network connection." }]);
    }
  };

  // Loan officer manual overrides
  const handleOfficerOverride = async (loanId: string, status: 'APPROVED' | 'REJECTED') => {
    const reason = prompt(`Enter manual override justification reason for ${status}:`);
    if (!reason) return;

    try {
      await axios.post(`http://localhost:8080/api/loans/${loanId}/override`, {
        status,
        overrideReason: reason
      }, {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });

      alert(`Loan overridden to ${status} successfully.`);
      fetchReviewQueue();
      fetchMetrics();
    } catch (err: any) {
      alert("Override failed: " + (err.response?.data?.message || err.message));
    }
  };

  return (
    <div className="min-h-screen bg-[#020617] text-slate-100 flex flex-col font-mono selection:bg-blue-500 selection:text-white antialiased">
      {/* Background radial glows */}
      <div className="fixed top-0 left-1/4 w-[600px] h-[600px] bg-blue-600/10 rounded-full blur-[140px] pointer-events-none" />
      <div className="fixed bottom-0 right-1/4 w-[500px] h-[500px] bg-indigo-500/5 rounded-full blur-[120px] pointer-events-none" />

      {/* Top Banner Navigation */}
      <header className="border-b border-slate-800/80 bg-slate-950/70 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 py-4 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center shadow-lg shadow-blue-500/20">
              <Activity className="w-5 h-5 text-white animate-pulse" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-widest bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">
                FINAGENT OS
              </h1>
              <div className="flex items-center space-x-2 text-xs text-blue-400">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
                <span>MISSION CONTROL CONSOLE</span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Role selection simulator */}
            <div className="flex items-center space-x-1 bg-slate-900 border border-slate-800 p-1 rounded-lg">
              <span className="text-[10px] text-slate-500 px-2 font-bold uppercase">Role:</span>
              {(['CUSTOMER', 'LOAN_OFFICER', 'RISK_ANALYST', 'ADMIN'] as const).map(role => (
                <button
                  key={role}
                  disabled={jwtToken !== null}
                  onClick={() => setCurrentRole(role)}
                  className={`px-2 py-1 rounded text-[9px] font-bold transition-all ${
                    currentRole === role
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-850 disabled:opacity-50'
                  }`}
                >
                  {role}
                </button>
              ))}
            </div>

            {jwtToken && (
              <button 
                onClick={handleLogout}
                className="px-3 py-1.5 border border-red-500/30 hover:border-red-500/60 bg-red-500/10 hover:bg-red-500/20 text-red-400 rounded-lg text-xs font-semibold uppercase transition-all"
              >
                Sign Out
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Core Screen */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 py-6 flex flex-col gap-6">
        {!jwtToken ? (
          // Auth Screen
          <div className="max-w-md w-full mx-auto my-auto p-6 rounded-2xl border border-slate-800/80 bg-slate-950/60 backdrop-blur-md shadow-2xl relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-[3px] bg-gradient-to-r from-blue-500 to-indigo-500" />
            <h2 className="text-md font-bold tracking-wider uppercase mb-4 text-center">
              Authenticate Account — {currentRole}
            </h2>
            
            {authError && (
              <div className="mb-4 p-3 rounded-lg border border-red-500/30 bg-red-500/10 text-red-400 text-xs flex items-center space-x-2">
                <AlertOctagon className="w-4 h-4" />
                <span>{authError}</span>
              </div>
            )}

            <form onSubmit={isRegistered ? handleLogin : handleRegister} className="space-y-4">
              {!isRegistered && (
                <div>
                  <label className="block text-[10px] text-slate-400 mb-1 uppercase font-bold">Full Name</label>
                  <input
                    type="text"
                    required
                    value={fullName}
                    onChange={e => setFullName(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 text-slate-100"
                  />
                </div>
              )}
              <div>
                <label className="block text-[10px] text-slate-400 mb-1 uppercase font-bold">Email Address</label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 text-slate-100"
                />
              </div>
              <div>
                <label className="block text-[10px] text-slate-400 mb-1 uppercase font-bold">Password</label>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 text-slate-100"
                />
              </div>

              <button
                type="submit"
                className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-bold uppercase transition-all shadow-lg shadow-blue-500/15"
              >
                {isRegistered ? "Access Control Console" : "Create Credential Profile"}
              </button>
            </form>

            <div className="mt-4 text-center">
              <button
                type="button"
                onClick={() => setIsRegistered(!isRegistered)}
                className="text-[11px] text-blue-400 hover:underline"
              >
                {isRegistered ? "Don't have an account? Sign up" : "Already have an account? Sign in"}
              </button>
            </div>
          </div>
        ) : (
          // Authenticated State Layout
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left & Middle Column: Interactive View Workspace */}
            <div className="lg:col-span-2 space-y-6">
              
              {/* Tab Navigators depending on selected role */}
              <div className="flex border-b border-slate-800 pb-2">
                {currentRole === 'CUSTOMER' && (
                  <div className="flex gap-2">
                    {(['apply', 'twin', 'health', 'chat'] as const).map(tab => (
                      <button
                        key={tab}
                        onClick={() => setCustomerTab(tab)}
                        className={`px-4 py-1.5 rounded-lg text-[10px] font-bold uppercase tracking-wider transition-all ${
                          customerTab === tab
                            ? 'bg-blue-600/20 text-blue-400 border border-blue-500/30'
                            : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        {tab === 'apply' ? 'Apply Loan' : tab === 'twin' ? 'Digital Twin' : tab === 'health' ? 'Advisor Health' : 'Support Chat'}
                      </button>
                    ))}
                  </div>
                )}

                {currentRole === 'LOAN_OFFICER' && (
                  <div className="flex gap-2">
                    {(['queue', 'audit'] as const).map(tab => (
                      <button
                        key={tab}
                        onClick={() => {
                          setOfficerTab(tab);
                          if (tab === 'audit') fetchAuditLogs();
                          if (tab === 'queue') fetchReviewQueue();
                        }}
                        className={`px-4 py-1.5 rounded-lg text-[10px] font-bold uppercase tracking-wider transition-all ${
                          officerTab === tab
                            ? 'bg-blue-600/20 text-blue-400 border border-blue-500/30'
                            : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        {tab === 'queue' ? 'Evaluation Queue' : 'System Audit Logs'}
                      </button>
                    ))}
                  </div>
                )}

                {currentRole === 'RISK_ANALYST' && (
                  <div className="flex gap-2">
                    {(['portfolio', 'agents'] as const).map(tab => (
                      <button
                        key={tab}
                        onClick={() => setAnalystTab(tab)}
                        className={`px-4 py-1.5 rounded-lg text-[10px] font-bold uppercase tracking-wider transition-all ${
                          analystTab === tab
                            ? 'bg-blue-600/20 text-blue-400 border border-blue-500/30'
                            : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        {tab === 'portfolio' ? 'Portfolio Monitoring' : 'AI Agent Operations'}
                      </button>
                    ))}
                  </div>
                )}

                {currentRole === 'ADMIN' && (
                  <div className="flex gap-2">
                    <button 
                      onClick={fetchAuditLogs}
                      className="px-4 py-1.5 rounded-lg text-[10px] font-bold uppercase bg-blue-600/20 text-blue-400 border border-blue-500/30"
                    >
                      Audit Operations Center
                    </button>
                  </div>
                )}
              </div>

              {/* View Workspace Contents */}
              
              {/* CUSTOMER: Apply Loan */}
              {currentRole === 'CUSTOMER' && customerTab === 'apply' && (
                <div className="space-y-6">
                  {/* Loan Request Form */}
                  <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm relative">
                    <div className="absolute top-0 left-0 w-full h-[2px] bg-blue-500/30" />
                    <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 mb-4 flex items-center space-x-2">
                      <FileText className="w-4 h-4 text-blue-400" />
                      <span>Apply for Loan Facility</span>
                    </h3>
                    
                    <form onSubmit={handleApplyLoan} className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <label className="block text-[10px] text-slate-500 mb-1 uppercase font-bold">Principal Amount ($)</label>
                        <input
                          type="number"
                          value={loanAmount}
                          onChange={e => setLoanAmount(Number(e.target.value))}
                          className="w-full bg-slate-900/60 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div>
                        <label className="block text-[10px] text-slate-500 mb-1 uppercase font-bold">Term Months</label>
                        <select
                          value={termMonths}
                          onChange={e => setTermMonths(Number(e.target.value))}
                          className="w-full bg-slate-900/60 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                        >
                          <option value={12}>12 Months</option>
                          <option value={24}>24 Months</option>
                          <option value={36}>36 Months</option>
                          <option value={60}>60 Months</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-[10px] text-slate-500 mb-1 uppercase font-bold">SSN (PII Identity)</label>
                        <input
                          type="text"
                          value={ssn}
                          onChange={e => setSsn(e.target.value)}
                          className="w-full bg-slate-900/60 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div>
                        <label className="block text-[10px] text-slate-500 mb-1 uppercase font-bold">Billing Address</label>
                        <input
                          type="text"
                          value={address}
                          onChange={e => setAddress(e.target.value)}
                          className="w-full bg-slate-900/60 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div>
                        <label className="block text-[10px] text-slate-500 mb-1 uppercase font-bold">Annual Gross Income ($)</label>
                        <input
                          type="number"
                          value={annualIncome}
                          onChange={e => setAnnualIncome(Number(e.target.value))}
                          className="w-full bg-slate-900/60 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                        />
                      </div>
                      <div>
                        <label className="block text-[10px] text-slate-500 mb-1 uppercase font-bold">Employment Status</label>
                        <select
                          value={employmentStatus}
                          onChange={e => setEmploymentStatus(e.target.value)}
                          className="w-full bg-slate-900/60 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
                        >
                          <option value="EMPLOYED">Employed</option>
                          <option value="SELF_EMPLOYED">Self-Employed</option>
                          <option value="UNEMPLOYED">Unemployed</option>
                        </select>
                      </div>

                      <div className="md:col-span-2 pt-2">
                        <button
                          type="submit"
                          className="w-full py-2.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white rounded-lg text-xs font-bold uppercase transition-all shadow-md shadow-blue-500/10 flex items-center justify-center space-x-2"
                        >
                          <Send className="w-3.5 h-3.5" />
                          <span>Submit Application to Event Pipeline</span>
                        </button>
                      </div>
                    </form>
                  </div>

                  {/* Active Application Evaluation Timeline & Decision details */}
                  {activeLoan && (
                    <div className="space-y-6 animate-fade-in">
                      {/* Timeline status bar */}
                      <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm">
                        <h4 className="text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-6">
                          Live Pipeline Workflow Status: {activeLoan.status}
                        </h4>

                        <div className="flex flex-col md:flex-row items-center justify-between gap-4 relative">
                          {/* Horizontal connection line */}
                          <div className="hidden md:block absolute top-[15px] left-8 right-8 h-[2px] bg-slate-800 z-0" />
                          
                          {/* Stage Node Application */}
                          <div className="flex flex-col items-center z-10">
                            <div className="w-8 h-8 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs font-bold ring-4 ring-blue-500/20">
                              1
                            </div>
                            <span className="text-[9px] font-bold uppercase mt-2 text-blue-400">Application</span>
                          </div>

                          {/* Stage Node Document Check */}
                          <div className="flex flex-col items-center z-10">
                            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
                              activeLoan.status !== 'SUBMITTED' ? 'bg-blue-600 text-white ring-4 ring-blue-500/20' : 'bg-slate-900 border border-slate-800 text-slate-500'
                            }`}>
                              2
                            </div>
                            <span className={`text-[9px] font-bold uppercase mt-2 ${
                              activeLoan.status !== 'SUBMITTED' ? 'text-blue-400' : 'text-slate-500'
                            }`}>Documents</span>
                          </div>

                          {/* Stage Node Risk Analysis */}
                          <div className="flex flex-col items-center z-10">
                            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
                              activeLoan.status !== 'SUBMITTED' ? 'bg-blue-600 text-white ring-4 ring-blue-500/20' : 'bg-slate-900 border border-slate-800 text-slate-500'
                            }`}>
                              3
                            </div>
                            <span className={`text-[9px] font-bold uppercase mt-2 ${
                              activeLoan.status !== 'SUBMITTED' ? 'text-blue-400' : 'text-slate-500'
                            }`}>Risk & Fraud</span>
                          </div>

                          {/* Stage Node Decision */}
                          <div className="flex flex-col items-center z-10">
                            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
                              activeLoan.status === 'APPROVED' ? 'bg-emerald-500 text-white ring-4 ring-emerald-500/20' :
                              activeLoan.status === 'REJECTED' ? 'bg-red-500 text-white ring-4 ring-red-500/20' :
                              activeLoan.status === 'UNDER_REVIEW' ? 'bg-yellow-500 text-white ring-4 ring-yellow-500/20' :
                              'bg-slate-900 border border-slate-800 text-slate-500'
                            }`}>
                              4
                            </div>
                            <span className={`text-[9px] font-bold uppercase mt-2 ${
                              activeLoan.status === 'APPROVED' ? 'text-emerald-400' :
                              activeLoan.status === 'REJECTED' ? 'text-red-400' :
                              activeLoan.status === 'UNDER_REVIEW' ? 'text-yellow-400' :
                              'text-slate-500'
                            }`}>Decision</span>
                          </div>
                        </div>
                      </div>

                      {/* Explainable AI Decision Panel */}
                      <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm relative overflow-hidden">
                        <div className="absolute top-0 left-0 w-full h-[2px] bg-indigo-500/30" />
                        <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 mb-4 flex items-center space-x-2">
                          <ShieldAlert className="w-4 h-4 text-indigo-400" />
                          <span>Explainable AI Decision Analytics</span>
                        </h4>

                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                          {/* Dial / Confidence meter */}
                          <div className="flex flex-col items-center justify-center p-4 border border-slate-900 rounded-xl bg-slate-950/80">
                            <div className="text-[10px] text-slate-500 uppercase font-bold mb-2">Confidence Match</div>
                            <div className="relative w-28 h-28 flex items-center justify-center">
                              <svg className="w-full h-full transform -rotate-90">
                                <circle cx="56" cy="56" r="44" className="stroke-slate-800 fill-none" strokeWidth="6" />
                                <circle cx="56" cy="56" r="44" className="stroke-indigo-500 fill-none" strokeWidth="6"
                                        strokeDasharray="276" strokeDashoffset={activeLoan.status === 'SUBMITTED' ? 276 : activeLoan.status === 'REJECTED' ? 80 : 20} />
                              </svg>
                              <div className="absolute flex flex-col items-center">
                                <span className="text-xl font-bold">{activeLoan.status === 'SUBMITTED' ? '0%' : activeLoan.status === 'REJECTED' ? '72%' : '94%'}</span>
                                <span className="text-[8px] text-slate-500 uppercase font-bold">Accuracy</span>
                              </div>
                            </div>
                          </div>

                          {/* Risk Chips / Key indicators */}
                          <div className="md:col-span-2 space-y-4">
                            <div className="grid grid-cols-3 gap-3">
                              <div className="p-3 border border-slate-900 rounded-lg bg-slate-950/80">
                                <div className="text-[8px] text-slate-500 uppercase font-bold">Risk Assessment</div>
                                <span className={`inline-block px-1.5 py-0.5 rounded text-[9px] font-bold mt-1 ${
                                  activeLoan.status === 'SUBMITTED' ? 'bg-slate-900 text-slate-500' :
                                  activeLoan.status === 'APPROVED' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                                  'bg-red-500/10 text-red-400 border border-red-500/20'
                                }`}>
                                  {activeLoan.status === 'SUBMITTED' ? 'PENDING' : activeLoan.status === 'APPROVED' ? 'LOW' : 'HIGH'}
                                </span>
                              </div>

                              <div className="p-3 border border-slate-900 rounded-lg bg-slate-950/80">
                                <div className="text-[8px] text-slate-500 uppercase font-bold">Fraud Pattern</div>
                                <span className={`inline-block px-1.5 py-0.5 rounded text-[9px] font-bold mt-1 ${
                                  activeLoan.status === 'SUBMITTED' ? 'bg-slate-900 text-slate-500' :
                                  activeLoan.status === 'APPROVED' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                                  'bg-yellow-500/10 text-yellow-400 border border-yellow-500/20'
                                }`}>
                                  {activeLoan.status === 'SUBMITTED' ? 'PENDING' : activeLoan.status === 'APPROVED' ? 'PASS' : 'WARNING'}
                                </span>
                              </div>

                              <div className="p-3 border border-slate-900 rounded-lg bg-slate-950/80">
                                <div className="text-[8px] text-slate-500 uppercase font-bold">Address Verif.</div>
                                <span className={`inline-block px-1.5 py-0.5 rounded text-[9px] font-bold mt-1 ${
                                  activeLoan.status === 'SUBMITTED' ? 'bg-slate-900 text-slate-500' :
                                  activeLoan.status === 'APPROVED' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                                  'bg-red-500/10 text-red-400 border border-red-500/20'
                                }`}>
                                  {activeLoan.status === 'SUBMITTED' ? 'PENDING' : activeLoan.status === 'APPROVED' ? 'PASS' : 'FLAGGED'}
                                </span>
                              </div>
                            </div>

                            {/* Decision Explanation Prose */}
                            <div className="p-3 border border-slate-900 rounded-lg bg-slate-950/80">
                              <div className="text-[9px] text-slate-500 uppercase font-bold mb-1">Supervisor Node Explanation</div>
                              <p className="text-[10px] leading-relaxed text-slate-300">
                                {activeLoan.explanation || "Evaluation in progress. Listening for Kafka decision event..."}
                              </p>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* CUSTOMER: Digital Twin */}
              {currentRole === 'CUSTOMER' && customerTab === 'twin' && (
                <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm space-y-6">
                  <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b border-slate-800 pb-3">
                    Customer Digital Twin Financial Metrics
                  </h3>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    {/* Credit standing dial */}
                    <div className="p-4 border border-slate-900 rounded-xl bg-slate-950/80 flex flex-col items-center">
                      <span className="text-[9px] text-slate-500 font-bold uppercase mb-2">Credit standing</span>
                      <div className="w-24 h-24 rounded-full border-4 border-indigo-500/30 flex flex-col items-center justify-center shadow-lg shadow-indigo-500/5">
                        <span className="text-xl font-bold text-indigo-400">720</span>
                        <span className="text-[8px] text-slate-500 font-bold">Good</span>
                      </div>
                      <span className="text-[8px] text-slate-500 mt-2 font-mono">Snapshot Bureau Check</span>
                    </div>

                    {/* Spend Donut */}
                    <div className="p-4 border border-slate-900 rounded-xl bg-slate-950/80 flex flex-col items-center">
                      <span className="text-[9px] text-slate-500 font-bold uppercase mb-2">Debt to Income</span>
                      <div className="w-24 h-24 rounded-full border-4 border-emerald-500/30 flex flex-col items-center justify-center">
                        <span className="text-xl font-bold text-emerald-400">18.3%</span>
                        <span className="text-[8px] text-slate-500 font-bold">Healthy</span>
                      </div>
                      <span className="text-[8px] text-slate-500 mt-2 font-mono">DTI Ratio Threshold</span>
                    </div>

                    {/* Monthly trend info */}
                    <div className="p-4 border border-slate-900 rounded-xl bg-slate-950/80 flex flex-col items-center justify-center">
                      <TrendingUp className="w-10 h-10 text-emerald-500 mb-2" />
                      <div className="text-center">
                        <span className="text-sm font-bold text-slate-200">+$2,450</span>
                        <p className="text-[9px] text-slate-500 font-bold uppercase mt-1">Average Savings Trend</p>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* CUSTOMER: Advisor Health */}
              {currentRole === 'CUSTOMER' && customerTab === 'health' && (
                <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm space-y-6">
                  <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b border-slate-800 pb-3 flex items-center space-x-2">
                    <Award className="w-4 h-4 text-emerald-400" />
                    <span>Financial Advisor recommendations</span>
                  </h3>

                  <div className="grid grid-cols-1 md:grid-cols-4 gap-4 items-center">
                    <div className="p-4 border border-slate-900 rounded-xl bg-slate-950/80 flex flex-col items-center col-span-1">
                      <span className="text-[8px] text-slate-500 font-bold uppercase mb-2">Health Index</span>
                      <div className="w-20 h-20 rounded-full border-4 border-emerald-500 flex items-center justify-center text-lg font-bold text-emerald-400">
                        {advisorScore}
                      </div>
                    </div>

                    <div className="md:col-span-3 space-y-3">
                      {advisorRecs.map((rec, index) => (
                        <div key={index} className="p-3 border border-slate-900 rounded-lg bg-slate-950/60 text-[10px] leading-relaxed flex items-start space-x-2">
                          <CheckCircle className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
                          <span>{rec}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* CUSTOMER: Support Chat */}
              {currentRole === 'CUSTOMER' && customerTab === 'chat' && (
                <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm flex flex-col h-[400px]">
                  <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b border-slate-800 pb-3 mb-4 flex items-center space-x-2">
                    <MessageSquare className="w-4 h-4 text-blue-400" />
                    <span>AI Customer Support Agent</span>
                  </h3>

                  {/* Chat messages */}
                  <div className="flex-1 overflow-y-auto space-y-3 mb-4 pr-2">
                    {chatMessages.map((msg, i) => (
                      <div key={i} className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                        <div className={`max-w-xs p-3 rounded-xl text-[10px] leading-relaxed ${
                          msg.sender === 'user' 
                            ? 'bg-blue-600/20 text-blue-300 border border-blue-500/30' 
                            : 'bg-slate-900 border border-slate-800 text-slate-300'
                        }`}>
                          {msg.text}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Form */}
                  <form onSubmit={handleSendMessage} className="flex gap-2">
                    <input
                      type="text"
                      value={chatInput}
                      onChange={e => setChatInput(e.target.value)}
                      placeholder="Ask about interest rates, credit score requirements, or status..."
                      className="flex-1 bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-[10px] text-slate-200 focus:outline-none focus:border-blue-500"
                    />
                    <button
                      type="submit"
                      className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-bold uppercase transition-all"
                    >
                      Send
                    </button>
                  </form>
                </div>
              )}

              {/* LOAN OFFICER: Review Queue */}
              {currentRole === 'LOAN_OFFICER' && officerTab === 'queue' && (
                <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm space-y-6">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                    <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400">
                      Applications Pending Manual Review
                    </h3>
                    <button 
                      onClick={fetchReviewQueue}
                      className="flex items-center space-x-1 px-2.5 py-1 rounded bg-slate-900 border border-slate-800 text-[10px] text-slate-400 hover:text-slate-200"
                    >
                      <RefreshCw className="w-3 h-3" />
                      <span>Refresh</span>
                    </button>
                  </div>

                  {loansQueue.length === 0 ? (
                    <div className="text-center py-8 text-xs text-slate-500 font-mono">
                      No applications currently flagged for review.
                    </div>
                  ) : (
                    <div className="space-y-4">
                      {loansQueue.map(loan => (
                        <div key={loan.id} className="p-4 border border-slate-900 rounded-xl bg-slate-950/80 space-y-3">
                          <div className="flex items-center justify-between">
                            <div>
                              <span className="text-[10px] text-slate-500 font-bold uppercase">Loan ID</span>
                              <div className="text-xs font-semibold font-mono text-slate-200">{loan.id}</div>
                            </div>
                            <div className="text-right">
                              <span className="text-[10px] text-slate-500 font-bold uppercase">Requested Amount</span>
                              <div className="text-xs font-bold text-slate-200">${loan.amount}</div>
                            </div>
                          </div>

                          <div className="p-3 border border-slate-900 rounded-lg bg-slate-950/60 text-[10px] leading-relaxed text-slate-400">
                            <strong>AI Explanation Summary:</strong> {loan.explanation}
                          </div>

                          <div className="flex items-center justify-end gap-2 pt-1 border-t border-slate-900">
                            <button
                              onClick={() => handleOfficerOverride(loan.id!, 'REJECTED')}
                              className="px-3 py-1.5 border border-red-500/30 hover:border-red-500/50 bg-red-500/5 hover:bg-red-500/10 text-red-400 rounded text-[9px] font-bold uppercase transition-all"
                            >
                              Override Reject
                            </button>
                            <button
                              onClick={() => handleOfficerOverride(loan.id!, 'APPROVED')}
                              className="px-3 py-1.5 border border-emerald-500/30 hover:border-emerald-500/50 bg-emerald-500/5 hover:bg-emerald-500/10 text-emerald-400 rounded text-[9px] font-bold uppercase transition-all"
                            >
                              Override Approve
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* AUDIT LOGS: Officer and Admin shared */}
              {((currentRole === 'LOAN_OFFICER' && officerTab === 'audit') || currentRole === 'ADMIN') && (
                <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm space-y-6">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                    <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400">
                      System Immutable Audit Ledger
                    </h3>
                    <button 
                      onClick={fetchAuditLogs}
                      className="flex items-center space-x-1 px-2.5 py-1 rounded bg-slate-900 border border-slate-800 text-[10px] text-slate-400 hover:text-slate-200"
                    >
                      <RefreshCw className="w-3 h-3" />
                      <span>Sync Ledger</span>
                    </button>
                  </div>

                  {auditLogs.length === 0 ? (
                    <div className="text-center py-8 text-xs text-slate-500">
                      No audit trails logged. Submit and process some loan applications to create logs.
                    </div>
                  ) : (
                    <div className="space-y-3 overflow-y-auto max-h-[400px] pr-2">
                      {auditLogs.map(audit => (
                        <div key={audit.id} className="p-3 border border-slate-900 rounded-lg bg-slate-950/80 font-mono text-[9px] space-y-1">
                          <div className="flex items-center justify-between text-slate-500">
                            <span>[AUDITED AT: {audit.auditedAt.replace('T', ' ').slice(0,19)}]</span>
                            <span className="font-bold text-slate-400">ID: {audit.id.slice(0,8)}</span>
                          </div>
                          <div>
                            <span className="text-slate-400">LOAN ID:</span> <span className="text-slate-300">{audit.loanId}</span>
                          </div>
                          <div>
                            <span className="text-slate-400">PRINCIPAL VALUE:</span> <span className="text-slate-300 font-bold">${audit.amount}</span>
                          </div>
                          <div>
                            <span className="text-slate-400">DECISION:</span> <span className={`font-bold ${
                              audit.status === 'APPROVED' ? 'text-emerald-400' : 'text-red-400'
                            }`}>{audit.status}</span>
                          </div>
                          <div className="text-slate-500 pt-1 leading-relaxed border-t border-slate-900">
                            {audit.explanation}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* RISK ANALYST: Portfolio flags */}
              {currentRole === 'RISK_ANALYST' && analystTab === 'portfolio' && (
                <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm space-y-6">
                  <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b border-slate-800 pb-3 flex items-center space-x-2">
                    <AlertTriangle className="w-4 h-4 text-yellow-400" />
                    <span>Portfolio Monitoring — Flagged Customers</span>
                  </h3>

                  <div className="space-y-4">
                    {riskFlags.map((flag, index) => (
                      <div key={index} className="p-4 border border-slate-900 rounded-xl bg-slate-950/80 flex items-start justify-between">
                        <div className="space-y-1">
                          <div className="flex items-center space-x-2">
                            <span className="text-xs font-bold text-slate-200">{flag.customerName}</span>
                            <span className="text-[8px] text-slate-500 font-mono">({flag.customerId})</span>
                          </div>
                          <p className="text-[10px] text-slate-400">{flag.description}</p>
                          <div className="text-[9px] text-yellow-500 font-bold">{flag.riskType}</div>
                        </div>
                        <span className={`px-2 py-0.5 rounded text-[8px] font-bold border ${
                          flag.severity === 'CRITICAL' ? 'bg-red-500/10 text-red-400 border-red-500/20' :
                          flag.severity === 'WARNING' ? 'bg-yellow-500/10 text-yellow-400 border-yellow-500/20' :
                          'bg-blue-500/10 text-blue-400 border-blue-500/20'
                        }`}>
                          {flag.severity}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* RISK ANALYST: AI Agent Operations status */}
              {currentRole === 'RISK_ANALYST' && analystTab === 'agents' && (
                <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm space-y-6">
                  <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b border-slate-800 pb-3">
                    Multi-Agent Core Operations Centre
                  </h3>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {agents.map(agent => (
                      <div key={agent.id} className="p-4 border border-slate-900 rounded-xl bg-slate-950/80 space-y-3 hover:border-slate-800 transition-all">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center space-x-2">
                            <Cpu className="w-4 h-4 text-blue-400 animate-pulse" />
                            <span className="text-xs font-bold text-slate-200">{agent.name}</span>
                          </div>
                          <span className="px-1.5 py-0.2 rounded text-[8px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                            {agent.status}
                          </span>
                        </div>
                        <div className="text-[10px] text-slate-400 leading-normal">{agent.role}</div>
                        <div className="grid grid-cols-3 gap-2 pt-2 border-t border-slate-900 text-[9px] font-mono">
                          <div>
                            <div className="text-slate-500">Latency</div>
                            <div className="text-slate-300 font-semibold">{agent.latency}</div>
                          </div>
                          <div>
                            <div className="text-slate-500">Load</div>
                            <div className="text-slate-300 font-semibold">{agent.load}%</div>
                          </div>
                          <div>
                            <div className="text-slate-500">Accuracy</div>
                            <div className="text-slate-300 font-semibold">{agent.accuracy}</div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Right Column: Ingress Gateway logs & Live Events Ticker */}
            <div className="space-y-6">
              
              {/* Analytics summary registry metrics tiles */}
              <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm space-y-4">
                <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400 border-b border-slate-800 pb-3 flex items-center space-x-2">
                  <BarChart3 className="w-4 h-4 text-blue-400" />
                  <span>Real-Time Aggregates</span>
                </h3>

                <div className="grid grid-cols-2 gap-4">
                  <div className="p-3 border border-slate-900 rounded-lg bg-slate-950/60">
                    <span className="text-[8px] text-slate-500 font-bold uppercase">Total Applied</span>
                    <div className="text-md font-bold text-slate-200">{metrics.totalLoansApplied}</div>
                  </div>
                  <div className="p-3 border border-slate-900 rounded-lg bg-slate-950/60">
                    <span className="text-[8px] text-slate-500 font-bold uppercase">Total Valuation</span>
                    <div className="text-md font-bold text-slate-200">${metrics.totalLoanAmount}</div>
                  </div>
                  <div className="p-3 border border-slate-900 rounded-lg bg-slate-950/60 col-span-2">
                    <span className="text-[8px] text-slate-500 font-bold uppercase">Rejected Rate</span>
                    <div className="text-md font-bold text-red-400">{(metrics.rejectedRate * 100).toFixed(0)}%</div>
                  </div>
                </div>
              </div>

              {/* Event Ticker logs list */}
              <div className="p-6 rounded-2xl border border-slate-800/80 bg-slate-950/40 backdrop-blur-sm flex flex-col h-[400px]">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-4">
                  <div className="flex items-center space-x-2">
                    <Terminal className="w-4 h-4 text-indigo-400 animate-pulse" />
                    <h3 className="text-xs font-bold uppercase tracking-widest text-slate-400">
                      Ingress Event Console
                    </h3>
                  </div>
                  <div className="flex items-center space-x-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-ping" />
                    <span className="text-[8px] text-slate-500 font-bold uppercase">Streaming</span>
                  </div>
                </div>

                <div className="flex-1 overflow-y-auto space-y-3 pr-1 scrollbar-thin scrollbar-thumb-slate-800">
                  {sseLogs.map(log => (
                    <div key={log.id} className="p-3 border border-slate-900 rounded-lg bg-slate-950/80 font-mono text-[9px] space-y-1">
                      <div className="flex items-center justify-between text-slate-500">
                        <span>[{log.timestamp}]</span>
                        <span className={`px-1 rounded text-[8px] font-bold uppercase ${
                          log.status === 'success' ? 'bg-emerald-500/10 text-emerald-400' :
                          log.status === 'error' ? 'bg-red-500/10 text-red-400' :
                          log.status === 'warning' ? 'bg-yellow-500/10 text-yellow-400' :
                          'bg-blue-500/10 text-blue-400'
                        }`}>
                          {log.source}
                        </span>
                      </div>
                      <div className="text-slate-300 font-semibold">{log.type}</div>
                      <p className="text-slate-400 leading-normal mt-0.5">{log.message}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
