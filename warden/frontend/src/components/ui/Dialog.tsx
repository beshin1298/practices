import { useEffect, useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
}

/** Native <dialog>: focus trap, Esc to close and inert background come from the browser. */
export function Dialog({ open, onClose, title, description, children }: DialogProps) {
  const titleId = useId(); // unique per dialog: several dialogs can be mounted at once
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(event) => event.target === ref.current && onClose()}
      aria-labelledby={titleId}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-card bg-surface p-0 text-ink shadow-pop backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
    >
      {open && (
        <div className="p-6">
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <h2 id={titleId} className="text-lg font-semibold">
                {title}
              </h2>
              {description && <p className="mt-1 text-sm text-muted">{description}</p>}
            </div>
            <button
              type="button"
              onClick={onClose}
              className="rounded-control p-1 text-muted hover:bg-canvas hover:text-ink"
              aria-label="Đóng"
            >
              <X className="size-5" />
            </button>
          </div>
          {children}
        </div>
      )}
    </dialog>
  );
}
