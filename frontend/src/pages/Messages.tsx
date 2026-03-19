export default function Messages() {
  // TODO: Fetch conversations from /api/messages (requires auth)
  // TODO: Implement real-time messaging (WebSocket or polling)
  return (
    <div>
      <h1 className="page-title">Messages</h1>
      <p className="page-subtitle">Chat with buyers and sellers.</p>
      <div className="placeholder-card">
        <p>💬 Your conversations will appear here.</p>
        {/* TODO: Render conversation list */}
        {/* TODO: Add chat window for selected conversation */}
      </div>
    </div>
  )
}
