import { useState } from 'react';
import { useParams } from 'react-router-dom';

type SignStep = 'ready' | 'pin' | 'signing' | 'done' | 'error';

export default function SignPage() {
  const { documentId } = useParams();
  const [step, setStep] = useState<SignStep>('ready');
  const [pin, setPin] = useState('');

  const handleSign = async () => {
    if (!pin || pin.length < 4) return;

    setStep('signing');

    // Simulated signing flow — in production this calls the browser extension
    // which interfaces with the CEI smart card via PKCS#11:
    //
    // 1. POST /api/sign/initiate { documentId } → { dtbsHash, signSessionId }
    // 2. extension.sign(dtbsHash, PIN) → { signatureValue, qesCertificate }
    // 3. POST /api/sign/complete { signSessionId, signatureValue, qesCertificate }

    await new Promise((resolve) => setTimeout(resolve, 2500));
    setStep('done');
  };

  return (
    <div className="auth-page">
      <div className="auth-card fade-in" style={{ maxWidth: '480px' }}>
        {step === 'ready' && (
          <>
            <div className="cei-icon">✍️</div>
            <h1>Semnare electronică calificată</h1>
            <p>
              Documentul <strong>#{documentId}</strong> este pregătit pentru semnare
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
              Se comunică cu Cartea de Identitate Electronică.
              <br />Nu scoateți cardul din cititor.
            </p>
            <div style={{
              marginTop: '24px',
              padding: '12px',
              background: '#f1f5f9',
              borderRadius: '8px',
              fontSize: '0.85rem',
              color: '#475569',
              fontFamily: 'monospace'
            }}>
              <div>✅ Sesiune PKCS#11 deschisă</div>
              <div>✅ PIN verificat</div>
              <div className="pulse">⏳ COMPUTE DIGITAL SIGNATURE...</div>
            </div>
          </>
        )}

        {step === 'done' && (
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
                  <strong>Nivel:</strong> PAdES-B-LTA ✅
                </div>
                <div style={{ marginBottom: '8px' }}>
                  <strong>Timestamp:</strong> {new Date().toISOString()} ✅
                </div>
                <div>
                  <strong>OCSP:</strong> GOOD ✅
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="btn btn-primary" style={{ flex: 1 }}>
                📥 Descarcă PDF semnat
              </button>
              <a href="/dashboard" className="btn btn-outline" style={{ flex: 1, textAlign: 'center', textDecoration: 'none' }}>
                ← Tablou de bord
              </a>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
