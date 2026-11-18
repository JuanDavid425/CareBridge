import type { NavItem } from '../navigation';

/** Heading and empty state for a section until its data is wired up. */
export function SectionPage({ item, emptyText }: { item: NavItem; emptyText: string }) {
  return (
    <section>
      <h1>{item.label}</h1>
      <p className="section__lead">{item.description}</p>
      <p className="empty-state">{emptyText}</p>
    </section>
  );
}
