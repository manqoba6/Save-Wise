/** Shared loading / error wrapper for each assistant tab. */
export default function Panel({ state, children }) {
  if (state.loading) return <p className="muted">Thinking...</p>;
  if (state.error) return <div className="alert">{state.error}</div>;
  return children(state.data);
}
