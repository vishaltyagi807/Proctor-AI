import { useRouter } from "@tanstack/react-router";

type HrefLinkProps = React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string };

export function HrefLink({ href, onClick, onMouseEnter, onFocus, target, ...props }: HrefLinkProps) {
  const router = useRouter();
  const preload = () => {
    void router.preloadRoute({ to: ".", href }).catch(() => undefined);
  };
  return (
    <a
      {...props}
      href={href}
      target={target}
      onMouseEnter={(event) => {
        onMouseEnter?.(event);
        preload();
      }}
      onFocus={(event) => {
        onFocus?.(event);
        preload();
      }}
      onClick={(event) => {
        onClick?.(event);
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || target) return;
        event.preventDefault();
        void router.navigate({ href });
      }}
    />
  );
}
