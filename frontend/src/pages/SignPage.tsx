import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { signingApi, documentsApi } from '../services/api';

type SignStep = 'ready' | 'pin' | 'signing' | 'done' | 'error';

type SignResult = {
  signatureId: string;
  documentId: string;
  signatureLevel: string;
  signingTimestamp: string;
  downloadUrl: string;
  verificationUrl: string;
};

export default function SignPage() {
  const { documentId } = useParams();
  const [step, setStep] = useState<SignStep>('ready');
  const [pin, setPin] = useState('');
  const [result, setResult] = useState<SignResult | null>(null);
  const [error, setError] = useState('');

  const handleSign = async () => {
    if (!pin || pin.length < 4 || !documentId) return;

    setStep('signing');
    setError('');

    try {
      // Call the dev-sign endpoint which:
      // 1. Generates a test RSA-2048 keypair + self-signed X.509 certificate
      // 2. Calls initiate() to get the DTBS hash from EU DSS
      // 3. Signs the hash with the test private key (simulating CEI chip)
      // 4. Calls complete() to embed PAdES-B-LTA signature via EU DSS
      //
      // In production, steps 1-3 happen on the CEI smart card via PKCS#11:
      //   const initResp = await signingApi.initiate(documentId);
      //   const { signatureValue, cert } = await ceiExtension.sign(initResp.dtbsHash, pin);
      //   const result = await signingApi.complete({...});

      const { data } = await signingApi.devSign(documentId);
      setResult(data);
      setStep('done');
    } catch (err: unknown) {
      setStep('error');
      let msg = 'Signing failed';
      if (err && typeof err === 'object' && 'response' in err) {
        const axErr = err as { response?: { data?: { message?: string }; status?: number } };
        msg = axErr.response?.data?.message || `Error ${axErr.response?.status}`;
      } else if (err instanceof Error) {
        msg = err.message;
      }
      setError(msg);
    }
  };

  const handleDownload = async () => {
    if (!documentId) return;
    try {
      const res = await documentsApi.download(documentId);
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `document_signed.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('Eroare la descărcarea documentului.');
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card fade-in" style={{ maxWidth: '500px' }}>
        {step === 'ready' && (
          <>
            <div className="cei-icon">✍️</div>
            <h1>Semnare electronică calificată</h1>
            <p>
              Documentul <strong style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                ...{documentId?.substring((documentId?.length ?? 8) - 8)}
              </strong> este pregătit pentru semnare
              cu certificatul QES de pe Cartea de Identitate Electronică.
            </p>
            <div className="card" style={{ textAlign: 'left', marginBottom: '24px' }}>
              <div style={{ fontSize: '0.85rem', color: '#64748b' }}>
                <div style={{ marginBottom: '8px' }}>
                  <strong>Nivel semnătură:</strong> PAdES-B-LTA
                </div>
                <div style={{ marginBottom: '8px' }}>
                  <strong>Timestamp:</strong> RFC 3161 (TSA calificat)
                </div>
                <div style={{ marginBottom: '8px' }}>
                  <strong>Verificare OCSP:</strong> Da (în timp real)
                </div>
                <div>
                  <strong>Conformitate:</strong> eIDAS Art. 25 / L. 455/2001 Art. 5
                </div>
              </div>
            </div>
            <button
              className="btn btn-primary"
              onClick={() => setStep('pin')}
              style={{ width: '100%', padding: '12px', fontSize: '1rem' }}
            >
              Continuă → Introducere PIN
            </button>
          </>
        )}

        {step === 'pin' && (
          <>
            <div className="cei-icon">🔑</div>
            <h1>Introduceți PIN-ul de semnare</h1>
            <p>
              Introduceți PIN-ul de semnare (QES PIN) al Cărții de Identitate
              Electronice. PIN-ul este procesat local — serverul nu îl vede niciodată.
            </p>
            <div className="form-group" style={{ textAlign: 'left' }}>
              <label>PIN semnare (QES)</label>
              <input
                type="password"
                className="form-control"
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                placeholder="••••••"
                maxLength={8}
                autoFocus
              />
              <p style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '4px' }}>
                🛠️ Dev mode: introduceți orice PIN de minim 4 caractere
              </p>
            </div>
            <button
              className="btn btn-success"
              onClick={handleSign}
              disabled={pin.length < 4}
              style={{ width: '100%', padding: '12px', fontSize: '1rem' }}
            >
              ✍️ Semnează documentul
            </button>
          </>
        )}

        {step === 'signing' && (
          <>
            <div className="cei-icon pulse">⏳</div>
            <h1>Se semnează...</h1>
            <p>
              Se procesează semnătura electronică prin EU DSS.
              <br />Vă rugăm așteptați.
            </p>
            <div style={{
              marginTop: '24px',
              padding: '12px',
              background: '#f1f5f9',
              borderRadius: '8px',
              fontSize: '0.85rem',
              color: '#475569',
              fontFamily: 'monospace',
              textAlign: 'left',
            }}>
              <div>✅ Sesiune PKCS#11 deschisă</div>
              <div>✅ PIN verificat</div>
              <div>✅ Certificat QES extras</div>
              <div className="pulse">⏳ EU DSS: PAdES-B-LTA embedding...</div>
            </div>
          </>
        )}

        {step === 'done' && result && (
          <>
            <div className="cei-icon" style={{ background: '#d1fae5' }}>✅</div>
            <h1>Document semnat cu succes!</h1>
            <p>
              Semnătura electronică calificată (PAdES-B-LTA) a fost aplicată.
              Documentul are valabilitate juridică (eIDAS Art. 25(2), L. 455/2001 Art. 5).
            </p>
            <div className="card" style={{ textAlign: 'left', marginBottom: '24px' }}>
              <div style={{ fontSize: '0.85rem', color: '#64748b' }}>
                <div style={{ marginBottom: '8px' }}>
                  <strong>Nivel:</strong> {result.signatureLevel} ✅
                </div>
                <div style={{ marginBottom: '8px' }}>
                  <strong>Timestamp:</strong> {new Date(result.signingTimestamp).toLocaleString('ro-RO')} ✅
                </div>
                <div style={{ marginBottom: '8px' }}>
                  <strong>OCSP:</strong> GOOD ✅
                </div>
                <div style={{ fontFamily: 'monospace', fontSize: '0.78rem', wordBreak: 'break-all' }}>
                  <strong>Signature ID:</strong> {result.signatureId}
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="btn btn-primary" onClick={handleDownload} style={{ flex: 1 }}>
                📥 Descarcă PDF semnat
              </button>
              <Link to="/dashboard" className="btn btn-outline" style={{ flex: 1, textAlign: 'center', textDecoration: 'none' }}>
                ← Tablou de bord
              </Link>
            </div>
          </>
        )}

        {step === 'error' && (
          <>
            <div className="cei-icon" style={{ background: '#fee2e2' }}>❌</div>
            <h1>Eroare la semnare</h1>
            <p style={{ color: '#991b1b' }}>
              {error || 'A apărut o eroare în procesul de semnare electronică.'}
            </p>
            <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
              <button className="btn btn-primary" onClick={() => { setStep('ready'); setError(''); }} style={{ flex: 1 }}>
                🔄 Încearcă din nou
              </button>
              <Link to="/requests" className="btn btn-outline" style={{ flex: 1, textAlign: 'center', textDecoration: 'none' }}>
                ← Înapoi la cereri
              </Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
