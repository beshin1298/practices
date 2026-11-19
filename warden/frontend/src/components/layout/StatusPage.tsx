import { useEffect } from 'react';
import { Link, useRouteError } from 'react-router-dom';

export function StatusPage({ code, title, message }: { code: string; title: string; message: string }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center px-4 text-center">
      <p className="text-sm font-semibold text-brand-700">{code}</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">{title}</h1>
      <p className="mt-3 max-w-md text-muted">{message}</p>
      <Link
        to="/account"
        className="mt-8 inline-flex h-10 items-center rounded-control border border-line-strong bg-surface px-4 text-sm font-semibold text-ink-soft shadow-card hover:bg-canvas"
      >
        Về trang tài khoản
      </Link>
    </div>
  );
}

export function ForbiddenPage() {
  return (
    <StatusPage
      code="403"
      title="Bạn không có quyền truy cập"
      message="Trang này chỉ dành cho quản trị viên. Nếu bạn nghĩ đây là nhầm lẫn, hãy liên hệ admin."
    />
  );
}

export function NotFoundPage() {
  return <StatusPage code="404" title="Không tìm thấy trang" message="Đường dẫn không tồn tại hoặc đã bị thay đổi." />;
}

/** errorElement of the router: a component that throws while rendering, instead of a blank page. */
export function RouteErrorPage() {
  const error = useRouteError();
  useEffect(() => console.error(error), [error]);
  return (
    <StatusPage
      code="Lỗi"
      title="Đã có lỗi xảy ra"
      message="Trang gặp lỗi không mong muốn. Hãy tải lại trang; nếu vẫn lỗi, liên hệ quản trị viên."
    />
  );
}
