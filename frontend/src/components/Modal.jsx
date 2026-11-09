import { useEffect } from 'react';

/** A centred dialog. Closes on Escape or a click on the dimmed background. */
export default function Modal({ title, onClose, children }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="between"><h2>{title}</h2><button className="icon" onClick={onClose} aria-label="Close">&times;</button></div>
        {children}
      </div>
    </div>
  );
}
