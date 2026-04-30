import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { requestsApi } from '../services/api';

type RequestData = {
  id: string;
  serviceType: string;
  status: string;
  citizenName: string;
  submittedAt: string | null;
  createdAt: string;
};

const SERVICE_LABELS: Record<string, string> = {
  CERTIFICAT_URBANISM: 'Certificat de Urbanism',
  GRANT_APPLICATION: 'Cerere Finanțare',
  AUTORIZATIE_CONSTRUIRE: 'Autorizație de Construire',
  ADEVERINTA_FISCALA: 'Adeverință Fiscală',
};

const STATUS_LABELS: Record<string, { label: string; css: string }> = {
  DRAFT: { label: 'Ciornă', css: 'badge-draft' },
  SUBMITTED: { label: 'Depusă', css: 'badge-submitted' },
  UNDER_REVIEW: { label: 'În analiză', css: 'badge-review' },
  APPROVED: { label: 'Aprobată', css: 'badge-approved' },
  REJECTED: { label: 'Respinsă', css: 'badge-rejected' },
};

export default function RequestsPage() {
  const role = localStorage.getItem('role') || 'CITIZEN';
  const isCitizen = role === 'CITIZEN';
  const isClerk = role === 'CLERK' || role === 'ADMIN';

  const [showForm, setShowForm] = useState(false);
  const [requests, setRequests] = useState<RequestData[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState<'my' | 'pending'>(isCitizen ? 'my' : 'pending');

  // Form state
  const [serviceType, setServiceType] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const fetchRequests = async () => {
    try {
      setLoading(true);
      let data;
      if (viewMode === 'pending') {
        const res = await requestsApi.findPending(0);
        data = res.data;
      } else {
        const res = await requestsApi.findMy(0);
        data = res.data;
      }
      setRequests(data.content);
    } catch (err) {
      console.error('Failed to fetch requests', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRequests();
  }, [viewMode]);

  const handleSubmit = async () => {
    if (!serviceType) {
      setError('Selectați tipul serviciului.');
      return;
    }
    
    setSubmitting(true);
    setError('');

    try {
      await requestsApi.create({
        serviceType,
        formData: { description }
      });
      
      setShowForm(false);
      setServiceType('');
      setDescription('');
      setViewMode('my');
      fetchRequests();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Eroare la crearea cererii';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const pageTitle = viewMode === 'pending' ? 'Cereri în așteptare' : 'Cererile mele';
  const pageDesc = viewMode === 'pending'
    ? 'Cereri depuse de cetățeni care așteaptă procesare'
    : 'Vizualizați și gestionați cererile pentru servicii municipale';

  return (
    <div className="app-layout">
      <aside className="app-sidebar">
        <div className="logo">
          <div className="icon">🏛️</div>
          <span>eMunicipalitate</span>
        </div>
        <nav>
          <Link to="/dashboard">📊 Tablou de bord</Link>
          <a
            href="#"
            className={viewMode === 'my' ? 'active' : ''}
            onClick={(e) => { e.preventDefault(); setViewMode('my'); }}
          >
            📋 Cererile mele
          </a>
          {isCitizen && (
            <a href="#" onClick={(e) => { e.preventDefault(); setShowForm(true); }}>
              📤 Depune cerere
            </a>
          )}
          {isClerk && (
            <a
              href="#"
              className={viewMode === 'pending' ? 'active' : ''}
              onClick={(e) => { e.preventDefault(); setViewMode('pending'); }}
            >
              👥 Cereri în așteptare
            </a>
          )}
        </nav>
        <div style={{ marginTop: 'auto', fontSize: '0.78rem', opacity: 0.5 }}>
          eIDAS LoA4 · PAdES-B-LTA
        </div>
      </aside>

      <main className="app-main">
        <div className="page-header fade-in">
          <h1>{pageTitle}</h1>
          <p>{pageDesc}</p>
        </div>

        <div style={{ display: 'flex', gap: '16px', marginBottom: '24px' }} className="fade-in">
          {isCitizen && (
            <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
              + Cerere nouă
            </button>
          )}
          <button className="btn btn-outline" onClick={fetchRequests}>
            🔄 Reîmprospătare
          </button>
        </div>

        {showForm && (
          <div className="card fade-in" style={{ marginBottom: '24px', background: '#f8fafc', border: '1px solid #cbd5e1' }}>
            <div className="card-header">
              <h2>Cerere nouă</h2>
              <button className="btn btn-outline" onClick={() => setShowForm(false)}>✕</button>
            </div>
            
            {error && (
              <div style={{ color: 'red', marginBottom: '12px', fontSize: '0.9rem' }}>
                {error}
              </div>
            )}

            <div className="form-group">
              <label>Tip serviciu</label>
              <select 
                className="form-control" 
                value={serviceType}
                onChange={(e) => setServiceType(e.target.value)}
              >
                <option value="">— Selectați —</option>
                <option value="CERTIFICAT_URBANISM">Certificat de Urbanism</option>
                <option value="GRANT_APPLICATION">Cerere Finanțare</option>
                <option value="AUTORIZATIE_CONSTRUIRE">Autorizație de Construire</option>
                <option value="ADEVERINTA_FISCALA">Adeverință Fiscală</option>
              </select>
            </div>
            <div className="form-group">
              <label>Descriere (opțional)</label>
              <textarea 
                className="form-control" 
                rows={3} 
                placeholder="Ex: Solicit certificat pentru adresa Str. Victoriei nr. 1..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            
            <div style={{ display: 'flex', gap: '8px' }}>
              <button 
                className="btn btn-primary" 
                onClick={handleSubmit}
                disabled={submitting}
              >
                {submitting ? 'Se trimite...' : '📤 Salvează cererea'}
              </button>
              <button className="btn btn-outline" onClick={() => setShowForm(false)}>Anulare</button>
            </div>
          </div>
        )}

        <div className="card fade-in">
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  {viewMode === 'pending' && <th>Cetățean</th>}
                  <th>Tip serviciu</th>
                  <th>Status</th>
                  <th>Data Creării</th>
                  <th>Acțiuni</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan={viewMode === 'pending' ? 6 : 5} style={{textAlign: 'center'}}>Se încarcă...</td></tr>
                ) : requests.length === 0 ? (
                  <tr><td colSpan={viewMode === 'pending' ? 6 : 5} style={{textAlign: 'center'}}>
                    {viewMode === 'pending' ? 'Nu există cereri în așteptare.' : 'Nu aveți nicio cerere momentan.'}
                  </td></tr>
                ) : (
                  requests.map((req) => (
                    <tr key={req.id}>
                      <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                        ...{req.id.substring(req.id.length - 8)}
                      </td>
                      {viewMode === 'pending' && (
                        <td>{req.citizenName}</td>
                      )}
                      <td>{SERVICE_LABELS[req.serviceType] || req.serviceType}</td>
                      <td>
                        <span className={`badge ${STATUS_LABELS[req.status]?.css || ''}`}>
                          {STATUS_LABELS[req.status]?.label || req.status}
                        </span>
                      </td>
                      <td>{new Date(req.createdAt).toLocaleString('ro-RO')}</td>
                      <td>
                        <Link to={`/requests/${req.id}`} className="btn btn-outline" style={{ marginRight: '4px', fontSize: '0.8rem', padding: '4px 8px', textDecoration: 'none' }}>
                          Detalii
                        </Link>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  );
}
