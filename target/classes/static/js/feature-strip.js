// Renders the "Quality Chickens / On-Time Delivery / Fair Price / Trust & Service"
// badge strip into any element with id="feature-strip".
const FEATURE_ITEMS = [
  { icon: 'badgeCheck', label: 'Quality Chickens' },
  { icon: 'clockFast', label: 'On-Time Delivery' },
  { icon: 'rupeeTag', label: 'Fair Price' },
  { icon: 'handshake', label: 'Trust & Service' },
];

function renderFeatureStrip(elementId) {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.innerHTML = FEATURE_ITEMS.map(f => `
    <div class="feature-item">
      <span class="feature-icon">${Icons[f.icon]}</span>
      <span class="feature-label">${f.label}</span>
    </div>
  `).join('');
}
