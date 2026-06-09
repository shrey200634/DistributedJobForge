import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Activity, CheckCircle, XCircle, Clock, Server, Play } from 'lucide-react';

const Dashboard = () => {
  const [metrics, setMetrics] = useState({
    submitted: 0,
    completed: 0,
    dlq: 0,
    throughput: 0
  });
  const [isRefreshing, setIsRefreshing] = useState(false);

  // Fetch metrics directly from Prometheus API (via Vite proxy)
  const fetchMetrics = async () => {
    try {
      setIsRefreshing(true);
      const queries = {
        submitted: '/prometheus/api/v1/query?query=djf_jobs_submitted_total',
        completed: '/prometheus/api/v1/query?query=djf_jobs_completed_total',
        dlq: '/prometheus/api/v1/query?query=djf_jobs_dlq_total',
        throughput: '/prometheus/api/v1/query?query=sum(rate(djf_jobs_completed_total[1m]))'
      };

      const [subRes, compRes, dlqRes, thruRes] = await Promise.all([
        axios.get(queries.submitted).catch(() => ({ data: { data: { result: [] } } })),
        axios.get(queries.completed).catch(() => ({ data: { data: { result: [] } } })),
        axios.get(queries.dlq).catch(() => ({ data: { data: { result: [] } } })),
        axios.get(queries.throughput).catch(() => ({ data: { data: { result: [] } } }))
      ]);

      const getValue = (res) => {
        const result = res.data?.data?.result;
        return result && result.length > 0 ? parseFloat(result[0].value[1]) : 0;
      };

      setMetrics({
        submitted: Math.floor(getValue(subRes)),
        completed: Math.floor(getValue(compRes)),
        dlq: Math.floor(getValue(dlqRes)),
        throughput: Math.floor(getValue(thruRes))
      });
    } catch (err) {
      console.error("Failed to fetch metrics", err);
    } finally {
      setTimeout(() => setIsRefreshing(false), 500);
    }
  };

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 3000);
    return () => clearInterval(interval);
  }, []);

  const triggerTestJob = async () => {
    try {
      await axios.post('/api/api/v1/jobs', {
        idempotencyKey: crypto.randomUUID(),
        type: 'SHELL',
        priority: 5,
        timeoutS: 60,
        maxRetries: 3,
        payload: { command: "echo hello from dashboard" }
      });
      // Force refresh metrics
      setTimeout(fetchMetrics, 500);
    } catch (err) {
      console.error("Failed to submit job", err);
    }
  };

  const StatCard = ({ title, value, icon: Icon, colorClass }) => (
    <div className="bg-slate-800/50 backdrop-blur-sm border border-slate-700/50 p-6 rounded-xl flex items-center justify-between transition-all duration-300 hover:border-slate-600">
      <div>
        <p className="text-slate-400 text-sm font-medium mb-1">{title}</p>
        <h3 className="text-3xl font-bold text-white tracking-tight">
          {value.toLocaleString()}
        </h3>
      </div>
      <div className={`p-4 rounded-full ${colorClass} bg-opacity-10`}>
        <Icon className={`w-8 h-8 ${colorClass.replace('bg-', 'text-').replace('/10', '')}`} />
      </div>
    </div>
  );

  return (
    <div className="min-h-screen p-8 max-w-7xl mx-auto font-sans">
      <header className="flex justify-between items-center mb-10">
        <div>
          <h1 className="text-3xl font-bold text-white flex items-center gap-3">
            <Server className="w-8 h-8 text-blue-500" />
            DistributedJobForge
          </h1>
          <p className="text-slate-400 mt-2">Production Task Execution Engine</p>
        </div>
        <div className="flex gap-4">
          <div className="flex items-center gap-2 px-4 py-2 bg-slate-800 rounded-full border border-slate-700">
            <div className={`w-2 h-2 rounded-full ${isRefreshing ? 'bg-yellow-500' : 'bg-green-500'}`}></div>
            <span className="text-sm text-slate-300">
              {isRefreshing ? 'Syncing...' : 'Live Connection'}
            </span>
          </div>
          <button 
            onClick={triggerTestJob}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-5 py-2 rounded-full font-medium transition-colors cursor-pointer"
          >
            <Play className="w-4 h-4" />
            Submit Test Job
          </button>
        </div>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <StatCard 
          title="Jobs Submitted" 
          value={metrics.submitted} 
          icon={Activity} 
          colorClass="bg-blue-500 text-blue-500" 
        />
        <StatCard 
          title="Jobs Completed" 
          value={metrics.completed} 
          icon={CheckCircle} 
          colorClass="bg-emerald-500 text-emerald-500" 
        />
        <StatCard 
          title="Dead Letter Queue" 
          value={metrics.dlq} 
          icon={XCircle} 
          colorClass="bg-red-500 text-red-500" 
        />
        <StatCard 
          title="Current Throughput (1m)" 
          value={`${metrics.throughput} /sec`} 
          icon={Clock} 
          colorClass="bg-purple-500 text-purple-500" 
        />
      </div>

      <div className="bg-slate-800/30 border border-slate-700/50 rounded-xl p-6">
        <h2 className="text-xl font-semibold text-white mb-4 flex items-center gap-2">
          <Activity className="w-5 h-5 text-slate-400" />
          System Overview
        </h2>
        <p className="text-slate-400 leading-relaxed max-w-3xl mb-6">
          This dashboard connects directly to the Prometheus metrics engine via Vite proxy, 
          bypassing the backend API entirely for true metric accuracy. It reads the 
          <code className="bg-slate-900 px-2 py-1 rounded mx-1 text-blue-400">djf.jobs.*</code> 
          counters published by the Micrometer registry.
        </p>
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-slate-900/50 p-4 rounded-lg border border-slate-700">
            <span className="text-slate-500 text-xs font-mono block mb-1">PROMETHEUS_TARGET</span>
            <span className="text-green-400 font-mono text-sm">http://localhost:9090</span>
          </div>
          <div className="bg-slate-900/50 p-4 rounded-lg border border-slate-700">
            <span className="text-slate-500 text-xs font-mono block mb-1">KAFKA_BROKERS</span>
            <span className="text-blue-400 font-mono text-sm">PLAINTEXT://kafka:9092</span>
          </div>
          <div className="bg-slate-900/50 p-4 rounded-lg border border-slate-700">
            <span className="text-slate-500 text-xs font-mono block mb-1">DATABASE_POOL</span>
            <span className="text-yellow-400 font-mono text-sm">HikariCP (Max: 10)</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
