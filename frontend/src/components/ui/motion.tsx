import { useEffect } from "react";
import { motion, useSpring, useTransform, type HTMLMotionProps } from "motion/react";
import { cn } from "@/lib/utils";

export const EASE = [0.22, 1, 0.36, 1] as const;

export function FadeIn({ children, delay = 0, y = 14, className, ...props }: { delay?: number; y?: number } & HTMLMotionProps<"div">) {
  return (
    <motion.div
      initial={{ opacity: 0, y }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay, ease: EASE }}
      className={className}
      {...props}
    >
      {children}
    </motion.div>
  );
}

const container = { hidden: {}, show: { transition: { staggerChildren: 0.06, delayChildren: 0.05 } } };
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.45, ease: EASE } } };

export function Stagger({ children, className, ...props }: HTMLMotionProps<"div">) {
  return (
    <motion.div variants={container} initial="hidden" animate="show" className={className} {...props}>
      {children}
    </motion.div>
  );
}

export function StaggerItem({ children, className, ...props }: HTMLMotionProps<"div">) {
  return (
    <motion.div variants={item} className={className} {...props}>
      {children}
    </motion.div>
  );
}

export function Hover({ children, className, lift = 4, ...props }: { lift?: number } & HTMLMotionProps<"div">) {
  return (
    <motion.div
      whileHover={{ y: -lift, transition: { duration: 0.2 } }}
      whileTap={{ scale: 0.985 }}
      className={className}
      {...props}
    >
      {children}
    </motion.div>
  );
}

export function AnimatedNumber({ value, className, suffix = "" }: { value: number; className?: string; suffix?: string }) {
  const spring = useSpring(0, { stiffness: 70, damping: 18 });
  const text = useTransform(spring, (latest) => `${Math.round(latest).toLocaleString()}${suffix}`);
  useEffect(() => {
    spring.set(value);
  }, [spring, value]);
  return <motion.span className={cn("tabular-nums", className)}>{text}</motion.span>;
}
