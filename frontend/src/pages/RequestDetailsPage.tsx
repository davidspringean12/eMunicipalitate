import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { requestsApi, documentsApi } from '../services/api';

/* ── Label maps ──────────────────────────────── */
const SERVICE_LABELS: Record<string, string> = {
  CERTIFICAT_URBANISM: 'Certificat de Urbanism',
  GRANT_APPLICATION: 'Cerere Finanțare',
  AUTORIZATIE_CONSTRUIRE: 'Autorizație de Construire',
  ADEVERINTA_FISCALA: 'Adeverință Fiscală',
};

const STATUS_META: Record<string, { label: string; css: string; icon: string }> = {
  DRAFT:        { label: 'Ciornă',      css: 'badge-draft',     icon: '📝' },
  SUBMITTED:    { label: 'Depusă',      css: 'badge-submitted', icon: '📩' },
  UNDER_REVIEW: { label: 'În analiză',  css: 'badge-review',    icon: '🔍' },
  APPROVED:     { label: 'Aprobată',    css: 'badge-approved',  icon: '✅' },
  REJECTED:     { label: 'Respinsă',    css: 'badge-rejected',  icon: '❌' },
};

/* ── Types ───────────────────────────────────── */
type RequestDetail = {
  id: string;
  citizenId: string;
  citizenName: string;
  assignedClerkId: string | null;
  assignedClerkName: string | null;
  serviceType: string;
  status: string;
  formData: Record<string, unknown>;
  rejectionReason: string | null;
  submittedAt: string | null;
  decisionAt: string | null;
  createdAt: string;
};

type DocumentItem = {
  id: string;
  requestId: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  sha256Hash: string;
  docType: string;
  signed: boolean;
  createdAt: string;
};

/* ── Component ───────────────────────────────── */
export default function RequestDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const role = localStorage.getItem('role') || 'CITIZEN';

  const [request, setRequest] = useState<RequestDetail | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Action states
  const [actionLoading, setActionLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [showRejectForm, setShowRejectForm] = useState(false);

  // File upload
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);

  const fetchData = useCallback(async () => {
    if (!id) return;
    try {
      setLoading(true);
      const [reqRes, docsRes] = await Promise.all([
        requestsApi.findById(id),
        documentsApi.findByRequest(id),
      ]);
      setRequest(reqRes.data);
      setDocuments(docsRes.data);
    } catch {
      setError('Nu s-a putut încărca cererea.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { fetchData(); }, [fetchData]);

  /* ── Actions ─────────────────────────────── */
  const handleSubmit = async () => {
    if (!id) return;
    setActionLoading(true);
    try {
      await requestsApi.submit(id);
      setActionMessage('Cererea a fost depusă cu succes!');
      fetchData();
    } catch {
      setActionMessage('Eroare la depunerea cererii.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!id) return;
    setActionLoading(true);
    try {
      await requestsApi.updateStatus(id, 'APPROVED');
      setActionMessage('Cererea a fost aprobată.');
      fetchData();
    } catch {
      setActionMessage('Eroare la aprobarea cererii.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!id || !rejectionReason.trim()) return;
    setActionLoading(true);
    try {
      await requestsApi.updateStatus(id, 'REJECTED', rejectionReason);
      setActionMessage('Cererea a fost respinsă.');
      setShowRejectForm(false);
      fetchData();
    } catch {
      setActionMessage('Eroare la respingerea cererii.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleTakeReview = async () => {
    if (!id) return;
    setActionLoading(true);
    try {
      await requestsApi.updateStatus(id, 'UNDER_REVIEW');
      setActionMessage('Cererea a fost preluată pentru analiză.');
      fetchData();
    } catch {
      setActionMessage('Eroare la preluarea cererii.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleUpload = async () => {
    if (!id || !selectedFile) return;
    setUploading(true);
    try {
      await documentsApi.upload(id, selectedFile);
      setSelectedFile(null);
      setActionMessage('Documentul a fost încărcat cu succes!');
      fetchData();
    } catch {
      setActionMessage('Eroare la încărcarea documentului.');
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (docId: string, filename: string) => {
    try {
      const res = await documentsApi.download(docId);
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setActionMessage('Eroare la descărcarea documentului.');
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  };

  const formatDate = (iso: string | null) =>
    iso ? new Date(iso).toLocaleString('ro-RO') : '—';

  /* ── Render ──────────────────────────────── */
  if (loading) {
    return (
      <div className="app-layout">
        <Sidebar role={role} />
        <main className="app-main">
          <div className="fade-in" style={{ textAlign: 'center', marginTop: '80px' }}>
            <p className="pulse" style={{ fontSize: '1.1rem', color: 'var(--color-text-secondary)' }}>
              Se încarcă detaliile cererii...
            </p>
          </div>
        </main>
      </div>
    );
  }

  if (error || !request) {
    return (
      <div className="app-layout">
        <Sidebar role={role} />
        <main className="app-main">
          <div className="fade-in" style={{ textAlign: 'center', marginTop: '80px' }}>
            <p style={{ color: 'var(--color-danger)' }}>{error || 'Cererea nu a fost găsită.'}</p>
            <button className="btn btn-outline" onClick={() => navigate('/requests')} style={{ marginTop: '16px' }}>
              ← Înapoi la cereri
            </button>
          </div>
        </main>
      </div>
    );
  }

  const st = STATUS_META[request.status] || { label: request.status, css: '', icon: '❓' };
  const isCitizen = role === 'CITIZEN';
  const isClerk = role === 'CLERK' || role === 'ADMIN';

  return (
    <div className="app-layout">
      <Sidebar role={role} />
      <main className="app-main">
        {/* ── Header ── */}
        <div className="page-header fade-in">
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '4px' }}>
            <Link to="/requests" style={{ color: 'var(--color-text-secondary)', textDecoration: 'none', fontSize: '0.9rem' }}>
              ← Cererile mele
            </Link>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <h1>{SERVICE_LABELS[request.serviceType] || request.serviceType}</h1>
            <span className={`badge ${st.css}`} style={{ fontSize: '0.85rem' }}>
              {st.icon} {st.label}
            </span>
          </div>
          <p style={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
            ID: {request.id}
          </p>
        </div>

        {/* ── Action message ── */}
        {actionMessage && (
          <div className="fade-in" style={{
            padding: '10px 16px',
            borderRadius: '8px',
            marginBottom: '20px',
            background: actionMessage.includes('succes') || actionMessage.includes('aprobată') || actionMessage.includes('preluată')
              ? '#d1fae5' : actionMessage.includes('respinsă') ? '#fef3cd' : '#fee2e2',
            color: actionMessage.includes('succes') || actionMessage.includes('aprobată') || actionMessage.includes('preluată')
              ? '#065f46' : actionMessage.includes('respinsă') ? '#92400e' : '#991b1b',
            fontSize: '0.9rem',
          }}>
            {actionMessage}
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: '24px', alignItems: 'start' }}>
          {/* ── Left column ── */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {/* Request Info */}
            <div className="card fade-in">
              <div className="card-header">
                <h2>Detalii cerere</h2>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                <InfoField label="Tip serviciu" value={SERVICE_LABELS[request.serviceType] || request.serviceType} />
                <InfoField label="Status" value={st.label} />
                <InfoField label="Cetățean" value={request.citizenName} />
                <InfoField label="Funcționar asignat" value={request.assignedClerkName || 'Neatribuit'} />
                <InfoField label="Data creării" value={formatDate(request.createdAt)} />
                <InfoField label="Data depunerii" value={formatDate(request.submittedAt)} />
                {request.decisionAt && (
                  <InfoField label="Data deciziei" value={formatDate(request.decisionAt)} />
                )}
              </div>

              {/* Form data */}
              {request.formData && Object.keys(request.formData).length > 0 && (
                <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid var(--color-border)' }}>
                  <p style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '8px', color: 'var(--color-text-secondary)' }}>
                    Date formular
                  </p>
                  {Object.entries(request.formData).map(([key, value]) => (
                    <div key={key} style={{ display: 'flex', gap: '8px', fontSize: '0.88rem', marginBottom: '4px' }}>
                      <span style={{ fontWeight: 500, color: 'var(--color-text-secondary)', minWidth: '120px' }}>
                        {key}:
                      </span>
                      <span>{String(value)}</span>
                    </div>
                  ))}
                </div>
              )}

              {/* Rejection reason */}
              {request.rejectionReason && (
                <div style={{
                  marginTop: '16px', padding: '12px', borderRadius: '8px',
                  background: '#fee2e2', border: '1px solid #fecaca',
                }}>
                  <p style={{ fontSize: '0.8rem', fontWeight: 600, color: '#991b1b', marginBottom: '4px' }}>
                    Motiv respingere
                  </p>
                  <p style={{ fontSize: '0.88rem', color: '#7f1d1d' }}>{request.rejectionReason}</p>
                </div>
              )}
            </div>

            {/* Documents */}
            <div className="card fade-in">
              <div className="card-header">
                <h2>📎 Documente ({documents.length})</h2>
              </div>

              {documents.length === 0 ? (
                <p style={{ color: 'var(--color-text-muted)', fontSize: '0.9rem' }}>
                  Nu există documente atașate.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {documents.map((doc) => (
                    <div key={doc.id} style={{
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      padding: '10px 14px', borderRadius: '8px',
                      background: 'var(--color-bg)', border: '1px solid var(--color-border)',
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span style={{ fontSize: '1.3rem' }}>
                          {doc.signed ? '✅' : doc.mimeType === 'application/pdf' ? '📄' : '📎'}
                        </span>
                        <div>
                          <p style={{ fontSize: '0.88rem', fontWeight: 500 }}>{doc.filename}</p>
                          <p style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>
                            {formatBytes(doc.sizeBytes)} · {doc.docType}
                            {doc.signed && ' · Semnat digital'}
                          </p>
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: '6px' }}>
                        <button
                          className="btn btn-outline"
                          style={{ fontSize: '0.78rem', padding: '4px 10px' }}
                          onClick={() => handleDownload(doc.id, doc.filename)}
                        >
                          ⬇ Descarcă
                        </button>
                        {request.status === 'APPROVED' && doc.mimeType === 'application/pdf' && !doc.signed && (
                          <Link
                            to={`/sign/${doc.id}`}
                            className="btn btn-success"
                            style={{ fontSize: '0.78rem', padding: '4px 10px' }}
                          >
                            ✍️ Semnează
                          </Link>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Upload section — only for DRAFT requests by citizens */}
              {isCitizen && request.status === 'DRAFT' && (
                <div style={{
                  marginTop: '16px', paddingTop: '16px',
                  borderTop: '1px solid var(--color-border)',
                }}>
                  <p style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '8px' }}>
                    Încarcă document
                  </p>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <input
                      type="file"
                      accept=".pdf,.doc,.docx,.jpg,.png"
                      onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                      className="form-control"
                      style={{ flex: 1 }}
                    />
                    <button
                      className="btn btn-primary"
                      onClick={handleUpload}
                      disabled={!selectedFile || uploading}
                      style={{ whiteSpace: 'nowrap' }}
                    >
                      {uploading ? 'Se încarcă...' : '📤 Încarcă'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* ── Right column — Actions & Timeline ── */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {/* Actions card */}
            <div className="card fade-in" style={{ background: '#f8fafc' }}>
              <div className="card-header">
                <h2>⚡ Acțiuni</h2>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {/* Citizen: Submit draft */}
                {isCitizen && request.status === 'DRAFT' && (
                  <button
                    className="btn btn-primary"
                    onClick={handleSubmit}
                    disabled={actionLoading}
                    style={{ width: '100%', padding: '12px', justifyContent: 'center' }}
                  >
                    {actionLoading ? 'Se procesează...' : '📩 Depune cererea la Primărie'}
                  </button>
                )}

                {/* Citizen: Waiting */}
                {isCitizen && request.status === 'SUBMITTED' && (
                  <div style={{
                    textAlign: 'center', padding: '16px', borderRadius: '8px',
                    background: '#dbeafe', color: '#1d4ed8', fontSize: '0.88rem',
                  }}>
                    ⏳ Cererea dumneavoastră este în curs de procesare.
                  </div>
                )}

                {/* Clerk: Take for review */}
                {isClerk && request.status === 'SUBMITTED' && (
                  <button
                    className="btn btn-primary"
                    onClick={handleTakeReview}
                    disabled={actionLoading}
                    style={{ width: '100%', padding: '12px', justifyContent: 'center' }}
                  >
                    {actionLoading ? 'Se procesează...' : '🔍 Preia pentru analiză'}
                  </button>
                )}

                {/* Clerk: Approve / Reject */}
                {isClerk && request.status === 'UNDER_REVIEW' && (
                  <>
                    <button
                      className="btn btn-success"
                      onClick={handleApprove}
                      disabled={actionLoading}
                      style={{ width: '100%', padding: '12px', justifyContent: 'center' }}
                    >
                      {actionLoading ? 'Se procesează...' : '✅ Aprobă cererea'}
                    </button>

                    {showRejectForm ? (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        <textarea
                          className="form-control"
                          rows={3}
                          placeholder="Motivul respingerii..."
                          value={rejectionReason}
                          onChange={(e) => setRejectionReason(e.target.value)}
                        />
                        <div style={{ display: 'flex', gap: '6px' }}>
                          <button
                            className="btn btn-danger"
                            onClick={handleReject}
                            disabled={actionLoading || !rejectionReason.trim()}
                            style={{ flex: 1 }}
                          >
                            Confirmă respingerea
                          </button>
                          <button
                            className="btn btn-outline"
                            onClick={() => setShowRejectForm(false)}
                          >
                            Anulare
                          </button>
                        </div>
                      </div>
                    ) : (
                      <button
                        className="btn btn-danger"
                        onClick={() => setShowRejectForm(true)}
                        style={{ width: '100%', padding: '12px', justifyContent: 'center' }}
                      >
                        ❌ Respinge cererea
                      </button>
                    )}
                  </>
                )}

                {/* Terminal states */}
                {request.status === 'APPROVED' && (
                  <div style={{
                    textAlign: 'center', padding: '16px', borderRadius: '8px',
                    background: '#d1fae5', color: '#065f46', fontSize: '0.88rem',
                  }}>
                    ✅ Cererea a fost aprobată.
                    {documents.some(d => d.mimeType === 'application/pdf' && !d.signed) && (
                      <p style={{ marginTop: '8px', fontSize: '0.82rem' }}>
                        Puteți semna documentele din secțiunea Documente.
                      </p>
                    )}
                  </div>
                )}

                {request.status === 'REJECTED' && (
                  <div style={{
                    textAlign: 'center', padding: '16px', borderRadius: '8px',
                    background: '#fee2e2', color: '#991b1b', fontSize: '0.88rem',
                  }}>
                    ❌ Cererea a fost respinsă.
                  </div>
                )}

                {/* Always show back button */}
                <button
                  className="btn btn-outline"
                  onClick={() => navigate('/requests')}
                  style={{ width: '100%', justifyContent: 'center', marginTop: '4px' }}
                >
                  ← Înapoi la cereri
                </button>
              </div>
            </div>

            {/* Timeline */}
            <div className="card fade-in">
              <div className="card-header">
                <h2>📅 Istoric</h2>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
                <TimelineEntry
                  icon="📝"
                  label="Cerere creată"
                  date={formatDate(request.createdAt)}
                  active
                />
                <TimelineEntry
                  icon="📩"
                  label="Depusă la Primărie"
                  date={formatDate(request.submittedAt)}
                  active={!!request.submittedAt}
                />
                {request.assignedClerkName && (
                  <TimelineEntry
                    icon="🔍"
                    label={`Preluată de ${request.assignedClerkName}`}
                    date=""
                    active
                  />
                )}
                {request.decisionAt && (
                  <TimelineEntry
                    icon={request.status === 'APPROVED' ? '✅' : '❌'}
                    label={request.status === 'APPROVED' ? 'Aprobată' : 'Respinsă'}
                    date={formatDate(request.decisionAt)}
                    active
                  />
                )}
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

/* ── Small helper components ─────────────────── */
function InfoField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: '2px', textTransform: 'uppercase', letterSpacing: '0.03em' }}>
        {label}
      </p>
      <p style={{ fontSize: '0.92rem' }}>{value}</p>
    </div>
  );
}

function TimelineEntry({ icon, label, date, active }: { icon: string; label: string; date: string; active: boolean }) {
  return (
    <div style={{
      display: 'flex', gap: '12px', alignItems: 'flex-start',
      paddingBottom: '16px', paddingLeft: '4px',
      borderLeft: '2px solid ' + (active ? 'var(--color-primary)' : 'var(--color-border)'),
      opacity: active ? 1 : 0.4,
    }}>
      <span style={{ marginLeft: '-11px', fontSize: '1.1rem', lineHeight: 1 }}>{icon}</span>
      <div>
        <p style={{ fontSize: '0.85rem', fontWeight: 500 }}>{label}</p>
        {date && date !== '—' && (
          <p style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>{date}</p>
        )}
      </div>
    </div>
  );
}

function Sidebar({ role }: { role: string }) {
  return (
    <aside className="app-sidebar">
      <div className="logo">
        <div className="icon">🏛️</div>
        <span>eMunicipalitate</span>
      </div>
      <nav>
        <Link to="/dashboard">📊 Tablou de bord</Link>
        <Link to="/requests" className="active">📋 Cererile mele</Link>
        <Link to="/requests">📤 Depune cerere</Link>
        {(role === 'CLERK' || role === 'ADMIN') && (
          <>
            <Link to="/requests">👥 Cereri în așteptare</Link>
            <Link to="/dashboard">📊 Rapoarte</Link>
          </>
        )}
      </nav>
      <div style={{ marginTop: 'auto', fontSize: '0.78rem', opacity: 0.5 }}>
        eIDAS LoA4 · PAdES-B-LTA
      </div>
    </aside>
  );
}
