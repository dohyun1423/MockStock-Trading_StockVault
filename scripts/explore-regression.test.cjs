const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/explore.js'), 'utf8');

function setup() {
    const elements = new Map();
    let document;
    class Element {
        constructor(tag) {
            this.tag = tag; this.children = []; this.dataset = {}; this.attributes = {};
            this.className = ''; this.listeners = {}; this.textContent = ''; this.replaceCount = 0;
            this.classList = {
                toggle: (name, active) => {
                    const names = new Set(this.className.split(' ').filter(Boolean));
                    if (active) names.add(name); else names.delete(name);
                    this.className = [...names].join(' ');
                }
            };
        }
        append(...items) { this.children.push(...items); }
        replaceChildren(...items) { this.children = items; this.replaceCount++; }
        setAttribute(key, value) { this.attributes[key] = value; }
        addEventListener(name, callback) { this.listeners[name] = callback; }
        focus() { document.activeElement = this; }
        querySelectorAll(selector) {
            const results = [];
            for (const child of this.children) {
                if (child.className.split(' ').includes(selector.slice(1))) results.push(child);
                results.push(...child.querySelectorAll(selector));
            }
            return results;
        }
    }
    for (const id of ['volume-ranking', 'ranking-updated', 'ranking-message']) elements.set(id, new Element('div'));
    document = {
        hidden: false, activeElement: null, addEventListener() {},
        createElement: (tag) => new Element(tag), getElementById: (id) => elements.get(id)
    };
    const calls = [];
    const timers = [];
    const item = { rank: 1, symbol: '005930', name: '삼성전자', currentPrice: 70000, changeRate: -1.25, volume: 1000, available: true };
    const snapshot = { items: [item], fetchedAt: '2026-11-03T00:00:00Z', stale: false };
    const context = vm.createContext({ document, window: { authReady: Promise.resolve(true), addEventListener() {} },
        setTimeout: (callback, delay) => { timers.push({ callback, delay }); return timers.length; },
        clearTimeout() {}, console,
        authFetch: async (url, options) => {
            calls.push({ url, options });
            return { ok: true, json: async () => url.includes('rankings') ? snapshot : [] };
        }
    });
    vm.runInContext(source, context);
    return { context, document, calls, timers, item, snapshot, list: elements.get('volume-ranking'),
        message: elements.get('ranking-message'), run: (code) => vm.runInContext(code, context) };
}

test('ranks show name/code, signed price change, and unchanged snapshots preserve rows', async () => {
    const app = setup();
    await app.run('refreshExplore()');
    assert.equal(app.list.children.length, 1);
    const row = app.list.children[0];
    const info = row.children[0].children[1];
    assert.equal(info.children[0].children[0].textContent, '삼성전자');
    assert.equal(info.children[0].children[1].textContent, '005930');
    assert.equal(info.children[1].children[1].textContent, '-1.25%');
    assert.match(info.children[1].children[1].className, /down/);
    assert.equal(row.children[0].href, '/stocks/detail?keyword=005930');
    const renders = app.list.replaceCount;
    await app.run('refreshExplore()');
    assert.equal(app.list.replaceCount, renders);
    assert.equal(app.timers.at(-1).delay, 60000);
});

test('hidden tabs make no ranking or watchlist requests', async () => {
    const app = setup();
    app.document.hidden = true;
    await app.run('refreshExplore()');
    assert.equal(app.calls.length, 0);
    assert.equal(app.timers.length, 0);
});

test('failed refresh retains previous rows and announces stale data', async () => {
    const app = setup();
    await app.run('refreshExplore()');
    app.context.authFetch = async () => { throw new Error('offline'); };
    await app.run('refreshExplore()');
    assert.equal(app.list.children.length, 1);
    assert.match(app.message.textContent, /마지막 조회 결과/);
    assert.equal(app.list.children[0].children[2].disabled, true);
});

test('heart uses existing watchlist API, blocks duplicate submits, and toggles removal', async () => {
    const app = setup();
    await app.run('refreshExplore()');
    let resolveRequest;
    const mutations = [];
    app.context.authFetch = async (url, options) => {
        mutations.push({ url, options });
        return new Promise((resolve) => { resolveRequest = resolve; });
    };
    const first = app.list.children[0].children[2].listeners.click();
    const duplicate = app.list.children[0].children[2].listeners.click();
    await duplicate;
    assert.equal(mutations.length, 1);
    assert.equal(mutations[0].options.method, 'POST');
    assert.equal(JSON.parse(mutations[0].options.body).stockName, '삼성전자');
    resolveRequest({ ok: true });
    await first;
    assert.equal(app.list.children[0].children[2].attributes['aria-pressed'], 'true');
    const removal = app.list.children[0].children[2].listeners.click();
    assert.equal(mutations[1].options.method, 'DELETE');
    assert.match(mutations[1].url, /stockName=/);
    resolveRequest({ ok: true });
    await removal;
    assert.equal(app.list.children[0].children[2].attributes['aria-pressed'], 'false');
});

test('failed heart mutation retains original favorite state and displays server message', async () => {
    const app = setup();
    await app.run('refreshExplore()');
    app.context.authFetch = async () => ({ ok: false, json: async () => ({ message: '최대 50개까지 저장할 수 있습니다.' }) });
    await app.list.children[0].children[2].listeners.click();
    assert.equal(app.list.children[0].children[2].attributes['aria-pressed'], 'false');
    assert.match(app.message.textContent, /최대 50개/);
});

test('unregistered stocks have detail links and usable hearts, and output is capped at 30', async () => {
    const app = setup();
    app.snapshot.items = Array.from({ length: 35 }, (_, index) => ({ ...app.item, rank: index + 1,
        symbol: String(index).padStart(6, '0'), name: '<img src=x>', available: false }));
    await app.run('refreshExplore()');
    assert.equal(app.list.children.length, 30);
    assert.equal(app.list.children[0].children[0].tag, 'a');
    assert.equal(app.list.children[0].children[0].href, '/stocks/detail?keyword=000000&source=explore');
    assert.equal(app.list.children[0].children[2].disabled, false);
    assert.equal(app.list.children[0].children[0].children[1].children[0].children[0].textContent, '<img src=x>');
});


test('unregistered heart prepares verified metadata before adding the canonical stock to watchlists', async () => {
    const app = setup();
    app.item.available = false;
    await app.run('refreshExplore()');
    const requests = [];
    app.context.authFetch = async (url, options) => {
        requests.push({ url, options });
        return { ok: true, json: async () => ({ symbol: '005930', name: '등록된 종목명' }) };
    };
    await app.list.children[0].children[2].listeners.click();
    assert.equal(requests.length, 2);
    assert.equal(requests[0].url, '/api/stocks/rankings/volume/005930/resolve');
    assert.equal(requests[0].options.method, 'POST');
    assert.equal(requests[1].url, '/api/watchlists');
    assert.equal(JSON.parse(requests[1].options.body).stockName, '등록된 종목명');
    assert.equal(app.list.children[0].children[2].attributes['aria-pressed'], 'true');
    assert.equal(app.list.children[0].children[0].href, '/stocks/detail?keyword=005930');
});

test('metadata failure leaves the heart unselected and does not attempt watchlist creation', async () => {
    const app = setup();
    app.item.available = false;
    await app.run('refreshExplore()');
    const requests = [];
    app.context.authFetch = async (url) => {
        requests.push(url);
        return { ok: false, json: async () => ({ message: '종목 정보를 확인하지 못했습니다.' }) };
    };
    await app.list.children[0].children[2].listeners.click();
    assert.deepEqual(requests, ['/api/stocks/rankings/volume/005930/resolve']);
    assert.equal(app.list.children[0].children[2].attributes['aria-pressed'], 'false');
    assert.equal(app.list.children[0].children[2].disabled, false);
    assert.match(app.message.textContent, /종목 정보를 확인하지 못했습니다/);
});

test('explore uses the same heart shape as the existing stock detail page', async () => {
    const app = setup();
    await app.run('refreshExplore()');
    const template = fs.readFileSync(path.join(__dirname, '../src/main/resources/templates/stock_detail.html'), 'utf8');
    const existingPath = template.match(/id="favorite-btn"[\s\S]*?<path d="([^"]+)"/)[1];
    assert.ok(app.list.children[0].children[2].innerHTML.includes(existingPath));
});

test('opening an unregistered explore stock in a new tab resolves metadata before rendering details', async () => {
    const detailSource = fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/stock_detail.js'), 'utf8');
    const requests = [];
    const context = vm.createContext({
        document: { addEventListener() {} }, URLSearchParams,
        window: { location: { search: '?keyword=Q530107&source=explore' } },
        authFetch: async (url, options) => {
            requests.push({ url, options });
            return { ok: true, json: async () => ({ symbol: 'Q530107', name: 'ETN' }) };
        }
    });
    vm.runInContext(detailSource, context);
    const stock = await vm.runInContext("fetchStockDetail('Q530107')", context);
    assert.equal(stock.symbol, 'Q530107');
    assert.equal(requests[0].url, '/api/stocks/rankings/volume/Q530107/resolve');
    assert.equal(requests[0].options.method, 'POST');
    context.window.location.search = '?keyword=005930';
    await vm.runInContext("fetchStockDetail('005930')", context);
    assert.equal(requests[1].url, '/api/stocks/symbol/005930');
});
