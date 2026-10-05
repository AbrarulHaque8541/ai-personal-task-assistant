(() => {
  'use strict';

  const STORAGE_KEY = 'daymark.tasks.v1';
  const BACKUP_KEY = 'daymark.tasks.v1.backup';
  const PRIORITIES = ['low', 'medium', 'high'];
  const logic = window.DaymarkLogic;
  const state = { tasks: [], filter: 'all', query: '', toastTimer: null, toastAction: null, currentDay: null, dayTimer: null };
  const $ = (selector) => document.querySelector(selector);
  const taskList = $('#task-list');
  const suggestionList = $('#suggestion-list');

  function setStorageStatus(message, unavailable = false) {
    const status = $('#storage-status');
    if (!status) return;
    const label = status.querySelector('span');
    if (label) label.textContent = message;
    status.classList.toggle('is-warning', unavailable);
  }

  function loadTasks() {
    let saved = null;
    try {
      saved = localStorage.getItem(STORAGE_KEY);
    } catch (error) {
      console.warn('Could not read Daymark storage:', error);
      setStorageStatus('Storage unavailable', true);
      showToast('Browser storage is unavailable. You can still use this page for now.');
      return [];
    }
    if (!saved) {
      setStorageStatus('Local storage ready');
      return [];
    }
    try {
      const parsed = JSON.parse(saved);
      if (!Array.isArray(parsed)) throw new Error('Saved task data is not a list.');
      const validated = logic.validateStoredTasks(parsed);
      if (validated.rejectedCount > 0) {
        setStorageStatus('Some saved tasks were ignored', true);
      } else {
        setStorageStatus('Saved on this device');
        // The main store is healthy again, so a recovery backup left over from an
        // earlier unreadable payload is now stale and should not linger as if it
        // were current data.
        clearRecoveryBackup();
      }
      return validated.tasks;
    } catch (error) {
      console.warn('Could not load saved Daymark tasks:', error);
      setStorageStatus('Saved data could not be read', true);
      // Preserve the unreadable payload instead of silently discarding it, so a
      // future fix or manual recovery can still reach the original data.
      let backupSaved = false;
      try {
        localStorage.setItem(BACKUP_KEY, saved);
        backupSaved = true;
      } catch (backupError) {
        console.warn('Could not keep a backup of unreadable Daymark tasks:', backupError);
      }
      showToast(backupSaved
        ? 'Saved tasks could not be read. The original data was kept in a browser backup.'
        : 'Saved tasks could not be read. Browser storage could not create a recovery backup; the original entry was left unchanged.');
      return [];
    }
  }

  function persistTasks() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state.tasks));
      setStorageStatus('Saved on this device');
      // A successful full save makes any earlier recovery backup stale.
      clearRecoveryBackup();
      return true;
    } catch (error) {
      console.warn('Could not save Daymark tasks:', error);
      setStorageStatus('Storage unavailable', true);
      return false;
    }
  }

  function clearRecoveryBackup() {
    try {
      localStorage.removeItem(BACKUP_KEY);
    } catch (error) {
      console.warn('Could not clear the Daymark recovery backup:', error);
    }
  }

  function showSaveResult(saved, successMessage) {
    showToast(saved ? successMessage : 'Could not save to this browser. Changes may be lost when you leave.');
  }

  function makeId() {
    if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') return globalThis.crypto.randomUUID();
    return `task-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 11)}`;
  }

  function escapeHTML(value) {
    return String(value).replace(/[&<>"']/g, (character) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
  }

  function readPriority(input) {
    // Guard against unexpected select values so every stored task stays valid
    // and reloadable by validateStoredTasks on the next visit.
    return PRIORITIES.includes(input.value) ? input.value : 'medium';
  }

  function formatDate(dateString) {
    if (!logic.isDateOnly(dateString)) return '';
    const [year, month, day] = dateString.split('-').map(Number);
    return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(new Date(year, month - 1, day));
  }

  function getDueText(task, today) {
    if (!task.dueDate) return 'No due date';
    if (task.dueDate < today) return `Overdue · ${formatDate(task.dueDate)}`;
    if (task.dueDate === today) return 'Due today';
    return `Due ${formatDate(task.dueDate)}`;
  }

  function getDueClass(task, today) {
    if (!task.dueDate) return '';
    if (task.dueDate < today) return 'is-overdue';
    if (task.dueDate === today) return 'is-today';
    return '';
  }

  function icon(name) {
    const paths = {
      calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/>',
      edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/>',
      trash: '<path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m4 4v6m6-6v6"/>',
      check: '<path d="m4 12 5 5L20 6"/>'
    };
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name]}</svg>`;
  }

  function taskMarkup(task, today) {
    const completionLabel = task.completed ? `Mark “${escapeHTML(task.title)}” incomplete` : `Complete “${escapeHTML(task.title)}”`;
    return `<article class="task-row${task.completed ? ' is-complete' : ''}" data-task-id="${escapeHTML(task.id)}">
      <button class="complete-button" type="button" data-action="toggle" aria-label="${completionLabel}" aria-pressed="${task.completed}">${icon('check')}</button>
      <div class="task-main">
        <span class="task-title" title="${escapeHTML(task.title)}">${escapeHTML(task.title)}</span>
        <div class="task-meta"><span class="due-label ${getDueClass(task, today)}">${icon('calendar')} ${escapeHTML(getDueText(task, today))}</span><span class="priority-pill priority-${task.priority}">${task.priority.toUpperCase()}</span></div>
      </div>
      <div class="task-actions">
        <button class="icon-button" type="button" data-action="edit" aria-label="Edit ${escapeHTML(task.title)}">${icon('edit')}</button>
        <button class="icon-button delete-button" type="button" data-action="delete" aria-label="Delete ${escapeHTML(task.title)}">${icon('trash')}</button>
      </div>
    </article>`;
  }

  function render() {
    const today = logic.localDateString();
    state.currentDay = today;
    const filteredTasks = logic.getFilteredTasks(state.tasks, state.filter, state.query, today);
    const openTasks = state.tasks.filter((task) => !task.completed);
    const todayTasks = state.tasks.filter((task) => task.dueDate === today);
    const upcomingTasks = state.tasks.filter((task) => task.dueDate !== null && task.dueDate > today);
    const completedTasks = state.tasks.filter((task) => task.completed);
    const hasQuery = Boolean(state.query.trim());

    $('#open-count').textContent = String(openTasks.length);
    $('#count-all').textContent = String(state.tasks.length);
    $('#count-today').textContent = String(todayTasks.length);
    $('#count-upcoming').textContent = String(upcomingTasks.length);
    $('#count-completed').textContent = String(completedTasks.length);
    $('#visible-count').textContent = hasQuery
      ? `${filteredTasks.length} matching ${filteredTasks.length === 1 ? 'task' : 'tasks'}`
      : `${filteredTasks.length} ${filteredTasks.length === 1 ? 'task' : 'tasks'}`;
    $('#today-label').textContent = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date()).toUpperCase();

    document.querySelectorAll('.filter-button').forEach((button) => {
      const active = button.dataset.filter === state.filter;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-pressed', String(active));
    });

    taskList.innerHTML = filteredTasks.map((task) => taskMarkup(task, today)).join('');
    const empty = filteredTasks.length === 0;
    $('#empty-state').hidden = !empty;
    taskList.hidden = empty;
    $('#empty-clear-search').hidden = !empty || !hasQuery;
    if (empty && hasQuery) {
      $('#empty-title').textContent = 'No matching tasks';
      $('#empty-copy').textContent = 'Try another word or clear your search to see the full list.';
    } else if (empty && state.filter === 'completed') {
      $('#empty-title').textContent = 'Nothing completed yet';
      $('#empty-copy').textContent = 'Finished tasks will be collected here for an easy review.';
    } else if (empty && state.tasks.length > 0) {
      const labels = {
        all: ['All caught up', 'Nothing on your list just now. Add a task whenever something comes to mind.'],
        today: ['Nothing due today', 'A little breathing room. Switch filters or add something new.'],
        upcoming: ['Nothing coming up', 'No future-dated tasks yet. Add a due date to plan ahead.']
      };
      $('#empty-title').textContent = labels[state.filter][0];
      $('#empty-copy').textContent = labels[state.filter][1];
    } else if (empty) {
      $('#empty-title').textContent = 'A clear start';
      $('#empty-copy').textContent = 'Add your first task above. Small steps count.';
    }

    const suggestions = logic.getSuggestions(state.tasks, today, formatDate);
    suggestionList.innerHTML = suggestions.length
      ? suggestions.map(({ task, reason }, index) => `<div class="suggestion-item"><span class="suggestion-number">${index + 1}</span><div><p class="suggestion-task-title">${escapeHTML(task.title)}</p><p class="suggestion-reason">${escapeHTML(reason)}</p></div></div>`).join('')
      : '<div class="suggestion-empty">Your open-task suggestions will show up here when you add a task.</div>';
  }

  function dismissToast() {
    clearTimeout(state.toastTimer);
    state.toastTimer = null;
    state.toastAction = null;
    $('#toast-stack').classList.remove('is-visible');
    $('#toast-action').hidden = true;
  }

  function showToast(message, action = null) {
    const toast = $('#toast');
    if (!toast) return;
    toast.textContent = message;
    const actionButton = $('#toast-action');
    actionButton.hidden = !action;
    actionButton.textContent = action?.label || '';
    state.toastAction = action;
    $('#toast-stack').classList.add('is-visible');
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(dismissToast, action ? 7000 : 2600);
  }

  function runToastAction() {
    const action = state.toastAction;
    if (!action) return;
    dismissToast();
    action.run();
  }

  function scheduleNextDayRefresh() {
    clearTimeout(state.dayTimer);
    const now = new Date();
    const nextMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0, 250);
    state.dayTimer = setTimeout(() => {
      if (logic.localDateString() !== state.currentDay) render();
      scheduleNextDayRefresh();
    }, Math.max(1000, nextMidnight.getTime() - now.getTime()));
  }

  function refreshIfLocalDayChanged() {
    if (logic.localDateString() === state.currentDay) return;
    render();
    scheduleNextDayRefresh();
  }

  function clearSearch() {
    state.query = '';
    $('#task-search').value = '';
    render();
    $('#task-search').focus();
  }

  function addTask(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const titleInput = $('#new-title');
    const title = titleInput.value.trim();
    const error = $('#add-error');
    if (!title) {
      error.hidden = false;
      titleInput.setAttribute('aria-invalid', 'true');
      titleInput.focus();
      return;
    }
    error.hidden = true;
    titleInput.removeAttribute('aria-invalid');
    const now = new Date().toISOString();
    const dueDateValue = $('#new-due-date').value;
    state.tasks.push({ id: makeId(), title, dueDate: logic.isDateOnly(dueDateValue) ? dueDateValue : null, priority: readPriority($('#new-priority')), completed: false, createdAt: now, updatedAt: now });
    const saved = persistTasks();
    form.reset();
    $('#new-priority').value = 'medium';
    state.filter = 'all';
    state.query = '';
    $('#task-search').value = '';
    render();
    titleInput.focus();
    showSaveResult(saved, 'Task added to your list.');
  }

  function openEditor(task) {
    $('#edit-id').value = task.id;
    $('#edit-title').value = task.title;
    $('#edit-due-date').value = task.dueDate || '';
    $('#edit-priority').value = task.priority;
    $('#edit-error').hidden = true;
    const dialog = $('#edit-dialog');
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
    $('#edit-title').focus();
  }

  function closeEditor() {
    const dialog = $('#edit-dialog');
    if (typeof dialog.close === 'function' && dialog.open) dialog.close();
    else dialog.removeAttribute('open');
  }

  function saveEdit(event) {
    event.preventDefault();
    const titleInput = $('#edit-title');
    const title = titleInput.value.trim();
    if (!title) {
      $('#edit-error').hidden = false;
      titleInput.setAttribute('aria-invalid', 'true');
      titleInput.focus();
      return;
    }
    titleInput.removeAttribute('aria-invalid');
    const task = state.tasks.find((item) => item.id === $('#edit-id').value);
    if (!task) return closeEditor();
    task.title = title;
    const dueDateValue = $('#edit-due-date').value;
    task.dueDate = logic.isDateOnly(dueDateValue) ? dueDateValue : null;
    task.priority = readPriority($('#edit-priority'));
    task.updatedAt = new Date().toISOString();
    const saved = persistTasks();
    render();
    closeEditor();
    showSaveResult(saved, 'Task updated.');
  }

  function handleTaskAction(event) {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const row = button.closest('[data-task-id]');
    const task = state.tasks.find((item) => item.id === row?.dataset.taskId);
    if (!task) return;
    if (button.dataset.action === 'toggle') {
      task.completed = !task.completed;
      task.updatedAt = new Date().toISOString();
      const saved = persistTasks();
      render();
      showSaveResult(saved, task.completed ? 'Task completed.' : 'Task reopened.');
    } else if (button.dataset.action === 'edit') {
      openEditor(task);
    } else if (button.dataset.action === 'delete') {
      const deletedIndex = state.tasks.findIndex((item) => item.id === task.id);
      const [deletedTask] = state.tasks.splice(deletedIndex, 1);
      const saved = persistTasks();
      render();
      showToast(saved ? 'Task deleted.' : 'Task deleted for this session. Saving failed.', {
        label: 'Undo',
        run: () => {
          if (state.tasks.some((item) => item.id === deletedTask.id)) {
            showToast('That task is already back on your list.');
            return;
          }
          state.tasks.splice(Math.min(deletedIndex, state.tasks.length), 0, deletedTask);
          const restored = persistTasks();
          render();
          showSaveResult(restored, 'Task restored.');
        }
      });
    }
  }

  function setUp() {
    state.tasks = loadTasks();
    render();
    $('#add-form').addEventListener('submit', addTask);
    $('#new-title').addEventListener('input', () => {
      $('#add-error').hidden = true;
      $('#new-title').removeAttribute('aria-invalid');
    });
    $('#task-search').addEventListener('input', (event) => {
      state.query = event.currentTarget.value;
      render();
    });
    $('#clear-search').addEventListener('click', clearSearch);
    $('#empty-clear-search').addEventListener('click', clearSearch);
    document.querySelectorAll('.filter-button').forEach((button) => button.addEventListener('click', () => {
      state.filter = button.dataset.filter;
      render();
    }));
    taskList.addEventListener('click', handleTaskAction);
    $('#edit-form').addEventListener('submit', saveEdit);
    $('#edit-title').addEventListener('input', () => {
      $('#edit-error').hidden = true;
      $('#edit-title').removeAttribute('aria-invalid');
    });
    document.querySelector('.close-dialog').addEventListener('click', closeEditor);
    document.querySelector('.cancel-edit').addEventListener('click', closeEditor);
    $('#toast-action').addEventListener('click', runToastAction);
    $('#edit-dialog').addEventListener('click', (event) => {
      if (event.target === $('#edit-dialog')) closeEditor();
    });
    window.addEventListener('storage', (event) => {
      if (event.key === STORAGE_KEY || event.key === null) {
        state.tasks = loadTasks();
        render();
      }
    });
    window.addEventListener('focus', refreshIfLocalDayChanged);
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) refreshIfLocalDayChanged();
    });
    scheduleNextDayRefresh();
  }

  setUp();
})();
