function openModal(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = 'flex';
}

function closeModal(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = 'none';
}

document.addEventListener('click', (e) => {
  const closeAttr = e.target.getAttribute && e.target.getAttribute('data-close-modal');
  if (closeAttr) closeModal(closeAttr);
  if (e.target.classList && e.target.classList.contains('modal-backdrop')) {
    e.target.style.display = 'none';
  }
});
