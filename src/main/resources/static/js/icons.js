// Small hand-drawn line-icon set (24x24, stroke=currentColor) so the app has
// its own consistent visual identity instead of emoji or a third-party icon font.
const Icons = {
  logo: `<svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
    <ellipse cx="24" cy="30" rx="14" ry="11" fill="currentColor" opacity="0.95"/>
    <circle cx="30" cy="16" r="8" fill="currentColor"/>
    <path d="M22 12c-2-3-6-3-7-1 2 0 3 1 4 2.5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round"/>
    <path d="M35 13c2-2 5-1.5 5 0.5-1.5-0.5-3 0-4 1.5" fill="currentColor"/>
    <path d="M36 17l4 1-4 1.6z" fill="#c98a2c"/>
    <circle cx="32.5" cy="14.5" r="1.3" fill="#163a24"/>
    <path d="M13 33c-2 1-3.5 3-3.5 5M35 33c2 1 3.5 3 3.5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none"/>
  </svg>`,

  dashboard: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>`,

  customers: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9.5 12 3l9 6.5"/><path d="M5 9v10a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9"/></svg>`,

  suppliers: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M2 16V7a1 1 0 0 1 1-1h9v10"/><path d="M12 10h5l4 4v2h-2"/><circle cx="7" cy="17.5" r="1.8"/><circle cx="16.5" cy="17.5" r="1.8"/><path d="M9 17.5h5.5"/></svg>`,

  orders: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="4" width="14" height="17" rx="1.5"/><path d="M9 3h6v3H9z"/><path d="M8 11h8M8 15h5"/></svg>`,

  deliveries: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v4"/><path d="M5 7h14"/><path d="M5 7 2 13a3 3 0 0 0 6 0L5 7Z"/><path d="M19 7l-3 6a3 3 0 0 0 6 0l-3-6Z"/><path d="M12 7v14"/><path d="M8 21h8"/></svg>`,

  purchases: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8l9-5 9 5-9 5-9-5Z"/><path d="M3 8v8l9 5 9-5V8"/><path d="M12 13v8"/></svg>`,

  payments: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9 8h4.5a2 2 0 1 1 0 4H9m0 0h6m-6 0v-2m0 2v3"/></svg>`,

  expenses: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3h9l3 3v15a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z"/><path d="M9 9h6M9 13h6M9 17h3"/></svg>`,

  reports: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20V10M10 20V4M16 20v-7M20 20H4"/></svg>`,

  logout: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/></svg>`,

  menu: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M3 12h18M3 18h18"/></svg>`,

  feather: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M20.24 3.76a6 6 0 0 1 0 8.49l-9 9L2 22l.76-9.24 9-9a6 6 0 0 1 8.48 0Z"/><path d="M8.5 15.5 15 9"/></svg>`,

  badgeCheck: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2l2.4 2.2 3.2-.4.7 3.2 2.9 1.5-1.3 3 1.3 3-2.9 1.5-.7 3.2-3.2-.4L12 22l-2.4-2.2-3.2.4-.7-3.2-2.9-1.5 1.3-3-1.3-3 2.9-1.5.7-3.2 3.2.4L12 2Z"/><path d="M9 12.5l2 2 4-4.5"/></svg>`,

  clockFast: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="13" r="8"/><path d="M12 9v4l3 2"/><path d="M9 2h6"/></svg>`,

  rupeeTag: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 7h6M9 10h6M9 7c3 0 3 3 0 3H9l5 6"/><rect x="3" y="4" width="18" height="16" rx="2"/></svg>`,

  handshake: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12l4-3 4 2 3-2 3 2h3"/><path d="M6 9l5 5-1.5 1.5a1.6 1.6 0 0 1-2.3 0L4 12"/><path d="M22 12l-4-3-1.5 1"/><path d="M18 9l-5 5 1.5 1.5a1.6 1.6 0 0 0 2.3 0L20 12"/></svg>`,

  truck: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M1 16V6a1 1 0 0 1 1-1h9v11"/><path d="M11 9h5l4 4v3h-2"/><circle cx="6" cy="18" r="1.8"/><circle cx="16.5" cy="18" r="1.8"/><path d="M8 18h6.5"/></svg>`,
  edit: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/></svg>`,

  trash: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16"/><path d="M10 11v6M14 11v6"/><path d="M6 7l1 14h10l1-14"/><path d="M9 7V4h6v3"/></svg>`,

};
