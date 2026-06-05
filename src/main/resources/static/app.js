'use strict';

/*
 * World Trending News — front-end controller.
 *
 * State model:
 *   appliedSettings / appliedResult  -> the "Snapshot" panel (last committed settings)
 *   previewSettings / previewResult  -> the "Preview" panel (live, edited but uncommitted)
 *
 * Editing any control mutates previewSettings and (debounced) re-queries the backend, so the
 * user always sees the effect of a change before committing it with "Apply".
 */

// ----------------------------------------------------------------------------
// Schema describing every filter and its tunable parameters. The control panel
// is generated from this, and settings objects are read/written through it.
// ----------------------------------------------------------------------------
const FILTERS = [
    {
        key: 'stopwords', label: 'Stopword removal',
        desc: 'Drop common low-signal words. The word list is editable below.',
        params: [{ kind: 'stopwords' }]
    },
    {
        key: 'blockedPhrases', label: 'Excluded phrases',
        desc: 'Never show these phrases as topics. One phrase per line; editable.',
        params: [{ kind: 'blockedPhrases' }]
    },
    {
        key: 'punctuation', label: 'Ignore punctuation',
        desc: 'Strip punctuation so "covid," and "covid" count as one term.',
        params: []
    },
    {
        key: 'plural', label: 'Collapse plurals',
        desc: 'Treat simple plurals as their singular ("banks" → "bank").',
        params: []
    },
    {
        key: 'phrase', label: 'Multi-word topics',
        desc: 'Surface phrases (n-grams), e.g. "donald trump", not just single words.',
        params: [{ kind: 'range', field: 'maxWords', min: 1, max: 5, step: 1, label: 'Max words per topic' }]
    },
    {
        key: 'multiWordOnly', label: 'Multi-word topics only',
        desc: 'Show only phrases of 2+ words, hiding all single-word topics.',
        params: []
    },
    {
        key: 'countOncePerArticle', label: 'Count once per article',
        desc: 'A topic adds to its score only once per article, so repeats within one story don\'t inflate it.',
        params: []
    },
    {
        key: 'phraseRollup', label: 'Roll up sub-phrases',
        desc: 'Count a shorter phrase toward the longer phrase that contains it (e.g. "trump" → "donald trump").',
        params: [{ kind: 'range', field: 'minContainerMentions', min: 1, max: 10, step: 1, label: 'Min mentions of longer phrase' }]
    },
    {
        key: 'mergeOverlap', label: 'Merge overlapping phrases',
        desc: 'Combine near-duplicate phrases from the same story (e.g. "drug boat kills" + "alleged drug boat") into one topic.',
        params: [{ kind: 'range', field: 'minArticleOverlap', min: 0.1, max: 1, step: 0.05, label: 'Min shared-article overlap' }]
    },
    {
        key: 'minLength', label: 'Minimum word length',
        desc: 'Discard very short tokens.',
        params: [{ kind: 'range', field: 'minLength', min: 1, max: 8, step: 1, label: 'Min characters' }]
    },
    {
        key: 'numeric', label: 'Hide number/date tokens',
        desc: 'Drop pure numbers and dates as topics ("250", "31st", "2026").',
        params: []
    },
    {
        key: 'noun', label: 'Noun detection (suffix)',
        desc: 'Keep words that look like nouns by their ending; drop obvious adverbs/verbs.',
        params: [{ kind: 'range', field: 'minLength', min: 2, max: 8, step: 1, label: 'Min length to test' }]
    },
    {
        key: 'capitalisation', label: 'Capitalisation → proper nouns',
        desc: 'Use mid-sentence capitalisation as a proper-noun signal.',
        params: [
            { kind: 'bool', field: 'requireCapitalised', label: 'Only keep proper nouns' },
            { kind: 'range', field: 'boost', min: 1, max: 5, step: 0.5, label: 'Score boost ×' }
        ]
    },
    {
        key: 'titleWeight', label: 'Title vs content weighting',
        desc: 'Give headline words more pull than body words.',
        params: [
            { kind: 'range', field: 'titleWeight', min: 0, max: 10, step: 0.5, label: 'Title weight' },
            { kind: 'range', field: 'contentWeight', min: 0, max: 5, step: 0.5, label: 'Content weight' }
        ]
    },
    {
        key: 'minSources', label: 'Minimum sources',
        desc: 'Only surface topics reported by several outlets.',
        params: [{ kind: 'range', field: 'minSources', min: 1, max: 10, step: 1, label: 'Distinct sources' }]
    },
    {
        key: 'minCountries', label: 'Multiple countries',
        desc: 'Only show topics carried by feeds from more than one country.',
        params: [{ kind: 'range', field: 'minCountries', min: 1, max: 6, step: 1, label: 'Distinct countries' }]
    },
    {
        key: 'region', label: 'Regions',
        desc: 'Restrict to selected regions (none selected = all regions).',
        params: [{ kind: 'regions', field: 'regions' }]
    },
    {
        key: 'recency', label: 'Recency',
        desc: 'Favour fresh news and drop stale articles.',
        params: [
            { kind: 'range', field: 'maxAgeHours', min: 1, max: 168, step: 1, label: 'Max age (hours)' },
            { kind: 'range', field: 'halfLifeHours', min: 0, max: 72, step: 1, label: 'Half-life (hours, 0 = no decay)' }
        ]
    },
    {
        key: 'hideSports', label: 'Hide sporting news',
        desc: 'Exclude articles about sport (football, cricket, the Olympics, etc.).',
        params: []
    },
    {
        key: 'hideIranWar', label: 'Hide Iran war news',
        desc: 'Exclude articles about the Iran conflict (Iran + war/strike/missile, etc.).',
        params: []
    },
    {
        key: 'hideMideastConflict', label: 'Hide Israel/Lebanon/Gaza conflict',
        desc: 'Exclude articles about the Israel, Lebanon and Gaza conflicts (place + war terms).',
        params: []
    }
];

let defaultSettings = null;
let appliedSettings = null;
let appliedResult = null;
let previewSettings = null;
let previewResult = null;
let previewTimer = null;
let previewInFlight = null; // promise for the current /api/trending fetch, or null
let previewRerunQueued = false; // a newer recompute was requested while one was in flight
let previewDirty = false;   // true when previewSettings changed but the panel hasn't refetched
let ALL_REGIONS = ['Australia', 'Americas', 'Europe', 'Asia']; // overwritten from /api/sources
let DEFAULT_BLOCKED_PHRASES = []; // loaded from /api/blocked-phrases

const $ = (id) => document.getElementById(id);
const clone = (o) => JSON.parse(JSON.stringify(o));

// ----------------------------------------------------------------------------
// Boot
// ----------------------------------------------------------------------------
async function boot() {
    defaultSettings = await fetchJson('/api/defaults');
    const stop = await fetchJson('/api/stopwords');
    defaultSettings.stopwords.words = stop.words || [];
    const blocked = await fetchJson('/api/blocked-phrases');
    DEFAULT_BLOCKED_PHRASES = blocked.phrases || [];
    defaultSettings.blockedPhrases.phrases = DEFAULT_BLOCKED_PHRASES.slice();

    appliedSettings = clone(defaultSettings);
    previewSettings = clone(defaultSettings);

    // Load sources first so the region list is known before the controls are built.
    await loadSources();
    buildControls();
    bindGlobalButtons();
    await refreshStatus();

    // First render: compute and apply immediately so both panels start populated.
    await runPreview();
    commitPreview();

    // Poll status periodically.
    setInterval(refreshStatus, 30000);
}

async function fetchJson(url, opts) {
    const res = await fetch(url, opts);
    if (!res.ok) throw new Error(url + ' -> ' + res.status);
    return res.json();
}

// ----------------------------------------------------------------------------
// Build the control panel from FILTERS + previewSettings
// ----------------------------------------------------------------------------
function buildControls() {
    syncTopN();
    const root = $('filterControls');
    root.innerHTML = '';

    FILTERS.forEach((f) => {
        const enabled = previewSettings[f.key].enabled;
        const wrap = document.createElement('div');
        wrap.className = 'filter' + (enabled ? ' on' : '');
        wrap.dataset.key = f.key;

        const head = document.createElement('div');
        head.className = 'filter-head';
        head.innerHTML =
            `<div class="filter-title"><b>${f.label}</b><small>${f.desc}</small></div>` +
            `<label class="switch"><input type="checkbox" ${enabled ? 'checked' : ''}/>` +
            `<span class="slider-toggle"></span></label>`;

        const checkbox = head.querySelector('input');
        checkbox.addEventListener('change', () => {
            previewSettings[f.key].enabled = checkbox.checked;
            wrap.classList.toggle('on', checkbox.checked);
            onSettingsChanged();
        });

        const body = document.createElement('div');
        body.className = 'filter-body';
        f.params.forEach((p) => body.appendChild(buildParam(f.key, p)));

        wrap.appendChild(head);
        if (f.params.length) wrap.appendChild(body);
        root.appendChild(wrap);
    });
}

function buildParam(filterKey, p) {
    if (p.kind === 'range') {
        const div = document.createElement('div');
        div.className = 'param';
        const value = previewSettings[filterKey][p.field];
        div.innerHTML =
            `<label>${p.label}<span class="val">${fmt(value)}</span></label>` +
            `<input type="range" min="${p.min}" max="${p.max}" step="${p.step}" value="${value}"/>`;
        const input = div.querySelector('input');
        const val = div.querySelector('.val');
        input.addEventListener('input', () => {
            const v = parseFloat(input.value);
            previewSettings[filterKey][p.field] = v;
            val.textContent = fmt(v);
            onSettingsChanged();
        });
        return div;
    }
    if (p.kind === 'bool') {
        const div = document.createElement('div');
        div.className = 'param bool';
        const checked = previewSettings[filterKey][p.field];
        div.innerHTML =
            `<label>${p.label}</label>` +
            `<label class="switch"><input type="checkbox" ${checked ? 'checked' : ''}/>` +
            `<span class="slider-toggle"></span></label>`;
        const input = div.querySelector('input');
        input.addEventListener('change', () => {
            previewSettings[filterKey][p.field] = input.checked;
            onSettingsChanged();
        });
        return div;
    }
    if (p.kind === 'stopwords') {
        const div = document.createElement('div');
        div.className = 'param';
        div.innerHTML =
            `<label>Word list <span class="val" id="stopwordCount"></span></label>` +
            `<textarea id="stopwordBox" spellcheck="false"></textarea>` +
            `<div class="stopword-actions">` +
            `<button class="ghost-btn" id="saveStopwords" title="Persist as the server default">Save default</button>` +
            `<button class="ghost-btn" id="resetStopwords">Reset list</button></div>`;
        // populate after insertion
        setTimeout(() => {
            const box = $('stopwordBox');
            box.value = (previewSettings.stopwords.words || []).join(', ');
            updateStopwordCount();
            box.addEventListener('input', () => {
                previewSettings.stopwords.words = parseWords(box.value);
                updateStopwordCount();
                onSettingsChanged();
            });
            $('saveStopwords').addEventListener('click', saveStopwordsAsDefault);
            $('resetStopwords').addEventListener('click', resetStopwords);
        }, 0);
        return div;
    }
    if (p.kind === 'blockedPhrases') {
        const div = document.createElement('div');
        div.className = 'param';
        div.innerHTML =
            `<label>Excluded phrases <span class="val" id="blockedCount"></span></label>` +
            `<textarea id="blockedBox" spellcheck="false" placeholder="one phrase per line"></textarea>` +
            `<div class="stopword-actions">` +
            `<button class="ghost-btn" id="resetBlocked">Reset list</button></div>`;
        setTimeout(() => {
            const box = $('blockedBox');
            box.value = (previewSettings.blockedPhrases.phrases || []).join('\n');
            updateBlockedCount();
            box.addEventListener('input', () => {
                previewSettings.blockedPhrases.phrases = parsePhrases(box.value);
                updateBlockedCount();
                onSettingsChanged();
            });
            $('resetBlocked').addEventListener('click', () => {
                previewSettings.blockedPhrases.phrases = DEFAULT_BLOCKED_PHRASES.slice();
                box.value = DEFAULT_BLOCKED_PHRASES.join('\n');
                updateBlockedCount();
                onSettingsChanged();
            });
        }, 0);
        return div;
    }
    if (p.kind === 'regions') {
        const div = document.createElement('div');
        div.className = 'param';
        const selected = previewSettings.region.regions || [];
        const boxes = ALL_REGIONS.map((r) => {
            const checked = selected.includes(r) ? 'checked' : '';
            return `<label class="region-check"><input type="checkbox" value="${r}" ${checked}/> ${r}</label>`;
        }).join('');
        div.innerHTML = `<label>Include regions</label><div class="region-checks">${boxes}</div>`;
        div.querySelectorAll('input[type=checkbox]').forEach((cb) => {
            cb.addEventListener('change', () => {
                const chosen = Array.from(div.querySelectorAll('input:checked')).map((c) => c.value);
                previewSettings.region.regions = chosen;
                onSettingsChanged();
            });
        });
        return div;
    }
    return document.createElement('div');
}

function parseWords(text) {
    return text.split(/[\s,]+/).map((w) => w.trim().toLowerCase()).filter(Boolean);
}

function updateStopwordCount() {
    const el = $('stopwordCount');
    if (el) el.textContent = (previewSettings.stopwords.words || []).length + ' words';
}

// One phrase per line; lower-cased, blanks dropped.
function parsePhrases(text) {
    return text.split('\n').map((p) => p.trim().toLowerCase()).filter(Boolean);
}

function updateBlockedCount() {
    const el = $('blockedCount');
    if (el) el.textContent = (previewSettings.blockedPhrases.phrases || []).length + ' phrases';
}

function fmt(v) {
    return String(v);
}

// ----------------------------------------------------------------------------
// Preview / apply lifecycle
// ----------------------------------------------------------------------------
function onSettingsChanged() {
    markDirty(true);
    clearTimeout(previewTimer);
    // Track that the on-screen preview no longer matches previewSettings, and give the
    // user immediate feedback that an update is coming (before the debounce fires).
    previewDirty = true;
    setUpdating(true);
    previewTimer = setTimeout(runPreview, 150); // debounce slider drags
}

// Toggle the "updating…" badge and dim the preview list while a recompute is pending.
function setUpdating(on) {
    const badge = $('previewUpdating');
    if (badge) badge.hidden = !on;
    const list = $('previewTopics');
    if (list) list.classList.toggle('updating', on);
}

// Re-query the backend for the current previewSettings.
//
// Single-flight: only one request is ever in flight. If a recompute is requested while one
// is running, we don't fire a parallel request — we just mark that another run is needed and
// the in-flight call re-runs once when it finishes (always with the latest settings). This
// prevents a queue of requests building up as the user drags sliders or toggles filters.
// Returns a promise that resolves once the preview reflects the current settings, so callers
// like Apply can await the freshest result.
function runPreview() {
    clearTimeout(previewTimer);
    setUpdating(true);

    // Already running: coalesce this request into the current one.
    if (previewInFlight) {
        previewRerunQueued = true;
        return previewInFlight;
    }

    previewInFlight = (async () => {
        // Keep fetching until the settings sent match the latest settings (no rerun queued).
        do {
            previewRerunQueued = false;
            const body = JSON.stringify(previewSettings);
            try {
                const result = await fetchJson('/api/trending', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body
                });
                previewResult = result;
                previewDirty = false;
                renderTopics('previewTopics', previewResult, appliedResult);
                renderMeta('previewMeta', previewResult);
            } catch (e) {
                $('previewTopics').innerHTML = `<li class="empty">Preview failed: ${e.message}</li>`;
            }
        } while (previewRerunQueued);
    })().finally(() => {
        previewInFlight = null;
        setUpdating(false);
    });
    return previewInFlight;
}

// Apply the previewed settings to the Snapshot panel. Crucially, if a debounced or
// in-flight preview fetch is still pending (e.g. the user toggled a filter and clicked
// Apply within the debounce window), wait for the freshest result first so the snapshot
// reflects the change rather than the previous state.
async function commitPreview() {
    if (previewDirty || previewInFlight) {
        const applyBtn = $('applyBtn');
        const label = applyBtn.textContent;
        applyBtn.disabled = true;
        applyBtn.textContent = 'Applying…';
        await runPreview();
        applyBtn.textContent = label;
    }
    appliedSettings = clone(previewSettings);
    appliedResult = previewResult;
    renderTopics('snapshotTopics', appliedResult, null);
    renderMeta('snapshotMeta', appliedResult);
    // Re-render preview so the up/down arrows reset relative to the new snapshot.
    renderTopics('previewTopics', previewResult, appliedResult);
    markDirty(false);
}

function revertPreview() {
    previewSettings = clone(appliedSettings);
    buildControls();
    runPreview();
    markDirty(false);
}

function markDirty(dirty) {
    $('applyBtn').disabled = !dirty;
    $('revertBtn').disabled = !dirty;
    $('dirtyBadge').hidden = !dirty;
}

// ----------------------------------------------------------------------------
// Rendering
// ----------------------------------------------------------------------------
function renderTopics(elementId, result, compareTo) {
    const ol = $(elementId);
    if (!result || !result.topics || result.topics.length === 0) {
        ol.innerHTML = '<li class="empty">No topics for these settings — loosen a filter.</li>';
        return;
    }
    const max = Math.max(...result.topics.map((t) => t.score), 1);
    const prevRank = {};
    if (compareTo && compareTo.topics) {
        compareTo.topics.forEach((t, i) => { prevRank[t.term] = i; });
    }

    ol.innerHTML = '';
    result.topics.forEach((t, i) => {
        const li = document.createElement('li');
        li.className = 'topic';
        if (compareTo) {
            if (!(t.term in prevRank)) li.classList.add('new');
            else if (prevRank[t.term] > i) li.classList.add('up');
            else if (prevRank[t.term] < i) li.classList.add('down');
        }
        const pct = Math.round((t.score / max) * 100);
        li.innerHTML =
            `<div class="body"><div class="term">${escapeHtml(t.term)}</div>` +
            `<div class="bar"><i style="width:${pct}%"></i></div></div>` +
            `<div class="stat"><b>${t.score}</b> score<br/>${t.mentions} hits · ${t.sourceCount} src</div>`;
        li.addEventListener('click', () => openArticles(t));
        ol.appendChild(li);
    });
}

function renderMeta(elementId, result) {
    if (!result) return;
    $(elementId).textContent = `${result.articlesConsidered}/${result.totalArticles} articles`;
}

function openArticles(topic) {
    $('dialogTitle').textContent = `“${topic.term}” — ${topic.articles.length} article(s)`;
    const ul = $('dialogArticles');
    ul.innerHTML = '';
    topic.articles.forEach((a) => {
        const li = document.createElement('li');
        const when = a.publishedAt ? timeAgo(a.publishedAt) : '';
        const sentence = a.sentence
            ? `<div class="sentence">…${highlightTerm(a.sentence, topic.term)}…</div>`
            : '';
        li.innerHTML =
            `<a href="${a.link}" target="_blank" rel="noopener">${escapeHtml(a.title)}</a>` +
            `<div class="src">${escapeHtml(a.sourceName)} · ` +
            `<span class="region">${escapeHtml(a.region || '')}</span> ${when ? '· ' + when : ''}</div>` +
            sentence;
        ul.appendChild(li);
    });
    $('articleDialog').showModal();
}

// ----------------------------------------------------------------------------
// Status & sources
// ----------------------------------------------------------------------------
async function refreshStatus() {
    try {
        const s = await fetchJson('/api/status');
        $('statusArticles').textContent = `${s.articles} articles`;
        $('statusSources').textContent = `${s.sources} sources`;
        $('statusUpdated').textContent = s.refreshing
            ? 'polling…'
            : (s.lastRefreshed ? 'updated ' + timeAgo(s.lastRefreshed) : 'not yet polled');
    } catch (e) { /* ignore transient */ }
}

async function loadSources() {
    try {
        const s = await fetchJson('/api/sources');
        ALL_REGIONS = Object.keys(s.byRegion);
        const strip = $('sourcesStrip');
        strip.innerHTML = `<span class="region-chip">📡 <b>${s.total}</b> feeds worldwide</span>`;
        Object.entries(s.byRegion).forEach(([region, names]) => {
            const chip = document.createElement('span');
            chip.className = 'region-chip';
            chip.title = names.join(', ');
            chip.innerHTML = `${region}: <b>${names.length}</b>`;
            strip.appendChild(chip);
        });
    } catch (e) { /* ignore */ }
}

// ----------------------------------------------------------------------------
// Global buttons / stopwords
// ----------------------------------------------------------------------------
// Reflect previewSettings.topN onto the slider + label (called on build/reset/revert).
function syncTopN() {
    const range = $('topNRange');
    if (!range) return;
    const v = previewSettings.topN || 20;
    range.value = v;
    $('topNVal').textContent = v;
}

function bindGlobalButtons() {
    $('topNRange').addEventListener('input', () => {
        const v = parseInt($('topNRange').value, 10);
        previewSettings.topN = v;
        $('topNVal').textContent = v;
        onSettingsChanged();
    });
    $('applyBtn').addEventListener('click', commitPreview);
    $('revertBtn').addEventListener('click', revertPreview);
    $('resetBtn').addEventListener('click', () => {
        previewSettings = clone(defaultSettings);
        buildControls();
        runPreview();
        markDirty(true);
    });
    $('refreshBtn').addEventListener('click', async () => {
        await fetch('/api/refresh', { method: 'POST' });
        $('statusUpdated').textContent = 'polling…';
        setTimeout(refreshStatus, 4000);
    });
    $('dialogClose').addEventListener('click', () => $('articleDialog').close());
    $('articleDialog').addEventListener('click', (e) => {
        if (e.target.id === 'articleDialog') $('articleDialog').close();
    });

    // Collapse / expand each section via its chevron button.
    document.querySelectorAll('.collapse-btn').forEach((btn) => {
        btn.addEventListener('click', () => {
            const section = document.getElementById(btn.dataset.target);
            if (!section) return;
            const collapsed = section.classList.toggle('collapsed');
            btn.setAttribute('aria-expanded', String(!collapsed));
        });
    });
}

async function saveStopwordsAsDefault() {
    const words = parseWords($('stopwordBox').value);
    await fetchJson('/api/stopwords', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ words })
    });
    flash($('saveStopwords'), 'Saved ✓');
}

async function resetStopwords() {
    const res = await fetchJson('/api/stopwords', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reset: true })
    });
    previewSettings.stopwords.words = res.words || [];
    $('stopwordBox').value = previewSettings.stopwords.words.join(', ');
    updateStopwordCount();
    onSettingsChanged();
}

function flash(btn, text) {
    const old = btn.textContent;
    btn.textContent = text;
    setTimeout(() => { btn.textContent = old; }, 1200);
}

// ----------------------------------------------------------------------------
// Utilities
// ----------------------------------------------------------------------------
function escapeHtml(s) {
    return (s || '').replace(/[&<>"']/g, (c) =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// Escape the sentence, then bold the first word of the term wherever it appears (the term
// may be normalised, so highlight on the leading word for a robust, case-insensitive match).
function highlightTerm(sentence, term) {
    const safe = escapeHtml(sentence);
    const firstWord = (term || '').split(' ')[0];
    if (!firstWord) return safe;
    const re = new RegExp('(' + firstWord.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'ig');
    return safe.replace(re, '<mark>$1</mark>');
}

function timeAgo(iso) {
    const then = new Date(iso).getTime();
    if (isNaN(then)) return '';
    const mins = Math.round((Date.now() - then) / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return mins + 'm ago';
    const hrs = Math.round(mins / 60);
    if (hrs < 24) return hrs + 'h ago';
    return Math.round(hrs / 24) + 'd ago';
}

boot();
