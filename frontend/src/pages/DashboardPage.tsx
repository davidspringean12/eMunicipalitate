import { Link } from 'react-router-dom';

export default function DashboardPage() {
  const fullName = localStorage.getItem('fullName') || 'Cetățean';
  const role = localStorage.getItem('role') || 'CITIZEN';

  return (
    <div className="app-layout">
      <Sidebar role={role} />
      <main className="app-main">
        <div className="page-header fade-in">
          <h1>Bun venit, {fullName}</h1>
          <p>Tablou de bord — servicii municipale digitale</p>
        </div>

        <div className="stats-grid fade-in">
          <div className="stat-card">
            <div className="stat-icon blue">📋</div>
            <div>
              <div className="stat-value">3</div>
              <div className="stat-label">Cereri active</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon gold">✍️</div>
            <div>
              <div className="stat-value">2</div>
              <div className="stat-label">Documente semnate</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon green">✅</div>
            <div>
              <div className="stat-value">5</div>
              <div className="stat-label">Cereri aprobate</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon red">⏳</div>
            <div>
              <div className="stat-value">~4 zile</div>
              <div className="stat-label">Timp mediu procesare</div>
            </div>
          </div>
        </div>

        <div className="card fade-in">
          <div className="card-header">
            <h2>Cereri recente</h2>
            <Link to="/requests" className="btn btn-outline">
              Vezi toate →
            </Link>
          </div>
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>Tip serviciu</th>
                  <th>Status</th>
                  <th>Data depunerii</th>
                  <th>Acțiuni</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Certificat de Urbanism</td>
                  <td><span className="badge badge-review">În analiză</span></td>
                  <td>12 Mar 2026</td>
                  <td><button className="btn btn-outline">Detalii</button></td>
                </tr>
                <tr>
                  <td>Cerere Finanțare</td>
                  <td><span className="badge badge-submitted">Depusă</span></td>
                  <td>10 Mar 2026</td>
                  <td><button className="btn btn-outline">Detalii</button></td>
                </tr>
                <tr>
                  <td>Certificat de Urbanism</td>
                  <td><span className="badge badge-approved">Aprobată</span></td>
                  <td>05 Mar 2026</td>
                  <td><button className="btn btn-outline">Detalii</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </main>
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
        <Link to="/dashboard" className="active">📊 Tablou de bord</Link>
        <Link to="/requests">📋 Cererile mele</Link>
        <Link to="/requests">📤 Depune cerere</Link>
        <Link to="/dashboard">📄 Documente</Link>
        {(role === 'CLERK' || role === 'ADMIN') && (
          <>
            <Link to="/dashboard">👥 Cereri în așteptare</Link>
            <Link to="/dashboard">📊 Rapoarte</Link>
          </>
        )}
        {role === 'ADMIN' && (
          <Link to="/dashboard">⚙️ Administrare</Link>
        )}
      </nav>
      <div style={{ marginTop: 'auto', fontSize: '0.78rem', opacity: 0.5 }}>
        eIDAS LoA4 · PAdES-B-LTA
      </div>
    </aside>
  );
}
