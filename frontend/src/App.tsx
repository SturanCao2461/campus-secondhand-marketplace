import { useEffect, useState } from 'react'

function App() {
    const [status, setStatus] = useState<string>('loading...')

    useEffect(() => {
        fetch('/api/health')
            .then(res => res.json())
            .then(data => setStatus(`${data.status} - ${data.service}`))
            .catch(() => setStatus('error: cannot reach backend'))
    }, [])

    return (
        <div style={{ padding: '2rem' }}>
            <h1>Campus Secondhand Marketplace</h1>
            <p>Backend status: <strong>{status}</strong></p>
        </div>
    )
}

export default App