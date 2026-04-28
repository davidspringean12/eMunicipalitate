import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi, decodeJwtPayload } from '../services/api';

export default function LoginPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle');
  const [error, setError] = useState('');
  const [devRole, setDevRole] = useState('CITIZEN');

  /**
   * Production flow: authenticate via CEI smart card + PKCS#11.
   * Currently not functional without a real CEI reader — use Dev Login below.
   */
  const handleCeiAuth = async () => {
    setStatus('loading');
    setError('');

    try {
      // Step 1: Get challenge nonce from server
      const { data: challenge } = await authApi.getChallenge();

      // Step 2: In production, the browser extension would interface with the
      // CEI smart card via PKCS#11 to sign the nonce.
      //
      // Real flow:
      //   const extension = await connectWebEID();
      //   const { signedNonce, authCertificate } = await extension.authenticate(challenge.nonce);

      // Simulated response (replace with Web eID extension call in production)
      const simulatedSignedNonce = btoa('simulated-signature-' + challenge.nonce);
      const simulatedCert = btoa('simulated-certificate');

      // Step 3: Verify with backend
      const { data: tokens } = await authApi.verify({
        sessionId: challenge.sessionId,
        signedNonce: simulatedSignedNonce,
        authCertificate: simulatedCert,
      });

      storeTokens(tokens);
      navigate('/dashboard');
    } catch (err: unknown) {
      setStatus('error');
      const message = err instanceof Error ? err.message : 'CEI authentication requires a real smart card reader.';
      setError(message);
    }
  };

  /**
   * Dev-only flow: get a test JWT without a real CEI card.
   */
  const handleDevLogin = async () => {
    setStatus('loading');
    setError('');

    try {
      const { data: tokens } = await authApi.devToken(devRole);
      storeTokens(tokens);
      navigate('/dashboard');
    } catch (err: unknown) {
      setStatus('error');
      const message = err instanceof Error ? err.message : 'Dev login failed';
      setError(message);
    }
  };

  const storeTokens = (tokens: { accessToken: string; refreshToken: string; fullName: string; role: string }) => {
    localStorage.setItem('accessToken', tokens.accessToken);
    localStorage.setItem('refreshToken', tokens.refreshToken);
    localStorage.setItem('fullName', tokens.fullName);
    localStorage.setItem('role', tokens.role);

    // Extract userId from JWT and store it
    const claims = decodeJwtPayload(tokens.accessToken);
    localStorage.setItem('userId', claims.sub as string);
  };

  return (
    <div className="auth-page">
      <div className="auth-card fade-in">
        <div className="cei-icon">🪪</div>
        <h1>eMunicipalitate</h1>
        <p>
          Autentificați-vă cu Cartea de Identitate Electronică (CEI)
          pentru a accesa serviciile municipale digitale.
        </p>

        {error && (
          <div style={{
            background: '#fee2e2',
            color: '#991b1b',
            padding: '8px 16px',
            borderRadius: '6px',
            marginBottom: '16px',
            fontSize: '0.85rem'
          }}>
            {error}
          </div>
        )}

        <button
          className="btn btn-primary"
          onClick={handleCeiAuth}
          disabled={status === 'loading'}
          style={{ width: '100%', padding: '12px', fontSize: '1rem' }}
        >
          {status === 'loading' ? (
            <span className="pulse">Se autentifică...</span>
          ) : (
            <>🔐 Autentificare cu CEI (LoA4)</>
          )}
        </button>

        {/* Dev login — only works when backend runs with spring.profiles.active=dev */}
        <div style={{
          marginTop: '24px',
          padding: '16px',
          background: '#f8fafc',
          borderRadius: '10px',
          border: '1px dashed #cbd5e1'
        }}>
          <p style={{ fontSize: '0.78rem', color: '#64748b', marginBottom: '12px' }}>
            🛠️ <strong>Dev Login</strong> — fără cititor CEI
          </p>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
            {(['CITIZEN', 'CLERK', 'ADMIN'] as const).map((role) => (
              <button
                key={role}
                onClick={() => setDevRole(role)}
                className={`btn ${devRole === role ? 'btn-primary' : 'btn-outline'}`}
                style={{ flex: 1, fontSize: '0.8rem', padding: '6px' }}
              >
                {role === 'CITIZEN' ? '👤' : role === 'CLERK' ? '📋' : '⚙️'} {role}
              </button>
            ))}
          </div>
          <button
            className="btn btn-success"
            onClick={handleDevLogin}
            disabled={status === 'loading'}
            style={{ width: '100%', padding: '10px', fontSize: '0.9rem' }}
          >
            Autentificare ca {devRole}
          </button>
        </div>

        <p style={{ marginTop: '16px', fontSize: '0.78rem', color: '#94a3b8' }}>
          Necesită un cititor NFC/contact sau aplicația ROeID.
          <br />
          Autentificare conformă cu eIDAS Art. 6 — Nivel de Asigurare Înalt.
        </p>
      </div>
    </div>
  );
}
