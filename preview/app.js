(function () {
  const app = document.getElementById('app');
  const state = {
    user: CZ.users[0],
    route: 'dashboard',
    outboundStep: 0,
    selectedBatch: CZ.batches[0].id,
    previewCode: 'MAT-NEW-000',
  };

  const menus = [
    { id: 'dashboard', label: '仪表盘' },
    { id: 'categories', label: '材料类目' },
    { id: 'materials', label: '材料台账' },
    { id: 'inbound', label: '入库管理' },
    { id: 'outbound', label: '扫码领用' },
    { id: 'returnScrap', label: '归还/报废' },
    { id: 'inventory', label: '库存批次' },
    { id: 'alerts', label: '库存预警' },
    { id: 'reports', label: '统计导出' },
    { id: 'users', label: '用户权限' },
  ];

  function toast(msg) {
    let el = document.querySelector('.toast');
    if (!el) {
      el = document.createElement('div');
      el.className = 'toast';
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.remove('show'), 2200);
  }

  function canManageDict() { return state.user?.role === 'admin'; }
  function canWriteStock() { return ['lab', 'admin', 'buyer'].includes(state.user?.role); }
  function canExport() { return state.user && state.user.role !== 'guest'; }

  function typeTag(type) {
    return `<span class="tag tag-${type.toLowerCase()}">${CZ.TYPE_LABEL[type]}</span>`;
  }

  function renderTree(nodes) {
    return `<ul>${nodes.map((n) => `
      <li>
        <span class="node">${n.name}（单位:${n.unit} · 安全库存:${n.safeStock}）</span>
        ${n.children ? renderTree(n.children) : ''}
      </li>`).join('')}</ul>`;
  }

  function pageHead(title, desc, actions = '') {
    return `<div class="page-head"><div><h2>${title}</h2><p>${desc}</p></div><div>${actions}</div></div>`;
  }

  function renderLogin() {
    return `
      <div class="login">
        <div class="login-card">
          <div class="brand">
            <div class="brand-mark">材</div>
            <div><h1>材智通</h1><p>智慧材料库数字化管理系统</p></div>
          </div>
          <p class="muted">把工场的“物资仓库”变成一键可查、全程可追溯、报表自动生成的云端材料银行。</p>
          <div class="field">
            <label>统一身份认证（LDAP 演示）</label>
            <select id="loginUser">
              ${CZ.users.map((u) => `<option value="${u.id}">${u.name} · ${CZ.ROLE_LABEL[u.role]}（${u.ldapUid}）</option>`).join('')}
            </select>
          </div>
          <button class="btn btn-primary btn-block" id="loginBtn">进入系统</button>
          <p class="muted" style="margin-top:14px;font-size:12px">权限：游客仅查 / 学生领用 / 实验员出入库 / 管理员字典维护</p>
        </div>
      </div>`;
  }

  function renderDashboard() {
    const low = CZ.materials.filter((m) => m.qty < m.safe).length;
    const outs = CZ.txns.filter((t) => t.type === 'OUT').length;
    const value = CZ.materials.reduce((s, m) => s + m.qty * m.price, 0).toFixed(0);
    const maxAmt = CZ.top10[0][2];
    return `
      <div class="page">
        ${pageHead('运营仪表盘', '月度 TOP10 耗材、资金占比与库存健康度一览')}
        <div class="stats">
          <div class="stat"><div class="label">在库 SKU</div><div class="value">${CZ.materials.length}</div></div>
          <div class="stat"><div class="label">本月领用单</div><div class="value">${outs}</div></div>
          <div class="stat warn"><div class="label">低于安全库存</div><div class="value">${low}</div></div>
          <div class="stat"><div class="label">库存估值（元）</div><div class="value">${value}</div></div>
        </div>
        <div class="grid-2">
          <div class="panel">
            <h3>月度 TOP10 耗材</h3>
            <div class="bar-list">
              ${CZ.top10.map(([name, count]) => `
                <div class="bar-row">
                  <div class="meta"><span>${name}</span><span>${count} 次</span></div>
                  <div class="bar"><span style="width:${Math.round((count / CZ.top10[0][1]) * 100)}%"></span></div>
                </div>`).join('')}
            </div>
          </div>
          <div class="panel">
            <h3>资金占比</h3>
            <div class="bar-list">
              ${CZ.top10.slice(0, 6).map(([name, , amt]) => `
                <div class="bar-row">
                  <div class="meta"><span>${name}</span><span>¥${amt}</span></div>
                  <div class="bar"><span style="width:${Math.round((amt / maxAmt) * 100)}%"></span></div>
                </div>`).join('')}
            </div>
          </div>
        </div>
        <div class="grid-2" style="margin-top:14px">
          <div class="panel">
            <h3>最近事务</h3>
            <table>
              <thead><tr><th>材料</th><th>类型</th><th>数量</th><th>人员</th><th>时间</th></tr></thead>
              <tbody>
                ${CZ.txns.map((t) => `<tr><td>${t.name}</td><td>${typeTag(t.type)}</td><td>${t.qty}</td><td>${t.user}</td><td>${t.time}</td></tr>`).join('')}
              </tbody>
            </table>
          </div>
          <div class="panel">
            <h3>预警快讯</h3>
            ${CZ.alerts.map((a) => `
              <div style="border:1px solid var(--line);border-radius:10px;padding:10px;margin-bottom:8px">
                <div style="display:flex;justify-content:space-between">
                  <strong>${a.name}</strong>
                  <span class="tag ${a.level === 'danger' ? 'tag-bad' : 'tag-warn'}">${a.level === 'danger' ? '紧急' : '关注'}</span>
                </div>
                <div class="muted" style="margin-top:6px">当前 ${a.qty} / 安全库存 ${a.safe}</div>
              </div>`).join('')}
          </div>
        </div>
      </div>`;
  }

  function renderCategories() {
    return `
      <div class="page">
        ${pageHead('材料类目树', '支持四级：大类 → 子类 → 品牌 → 型号/批次',
          `<button class="btn btn-primary" id="addCat" ${canManageDict() ? '' : 'disabled'}>新增类目</button>`)}
        ${canManageDict() ? '' : '<div class="hint">当前角色仅可查看；字典维护需管理员权限。</div>'}
        <div class="tree">${renderTree(CZ.categories)}</div>
      </div>`;
  }

  function renderMaterials() {
    return `
      <div class="page">
        ${pageHead('材料台账', '品牌 / 型号 / 规格 / 单价，对应 material_item')}
        <table>
          <thead><tr><th>类目路径</th><th>品牌</th><th>型号</th><th>规格</th><th>单价</th><th>单位</th><th>库存</th></tr></thead>
          <tbody>
            ${CZ.materials.map((m) => `
              <tr>
                <td>${m.path}</td><td>${m.brand}</td><td>${m.model}</td><td>${m.spec}</td>
                <td>CNY ${m.price.toFixed(2)}</td><td>${m.unit}</td>
                <td><span class="tag ${m.qty < m.safe ? 'tag-bad' : 'tag-ok'}">${m.qty} / 安全 ${m.safe}</span></td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  }

  function renderInbound() {
    return `
      <div class="page">
        ${pageHead('入库管理', '扫码/手动录入，自动生成批次号与电子标签二维码')}
        <div class="form-grid">
          <form id="inboundForm">
            <div class="field"><label>材料</label>
              <select name="itemId" required>
                ${CZ.materials.map((m) => `<option value="${m.id}">${m.brand} ${m.model} · ${m.spec}</option>`).join('')}
              </select>
            </div>
            <div class="form-row">
              <div class="field"><label>入库数量</label><input name="qty" type="number" min="1" value="10" required ${canWriteStock() ? '' : 'disabled'}></div>
              <div class="field"><label>货架位</label><input name="loc" placeholder="如 A-01-03" required ${canWriteStock() ? '' : 'disabled'}></div>
              <div class="field"><label>有效期</label><input name="expire" placeholder="YYYY-MM-DD" required ${canWriteStock() ? '' : 'disabled'}></div>
            </div>
            <div class="field"><label>条码（可扫码枪填入）</label><input name="barcode" placeholder="扫码或留空自动生成" ${canWriteStock() ? '' : 'disabled'}></div>
            <button class="btn btn-primary" ${canWriteStock() ? '' : 'disabled'}>确认入库并打印标签</button>
          </form>
          <div class="panel">
            <h3>电子标签预览</h3>
            <div style="display:flex;gap:14px;align-items:center">
              <div class="qr">${state.previewCode}</div>
              <div><div>二维码内容</div><strong>${state.previewCode}</strong>
              <p class="muted" style="margin-top:8px">入库后可打印贴标，货架位与批次绑定。</p></div>
            </div>
          </div>
        </div>
        <h3 style="color:var(--teal);margin-top:20px">近期入库批次</h3>
        <table>
          <thead><tr><th>批次号</th><th>材料</th><th>数量</th><th>条码</th><th>货架</th><th>有效期</th></tr></thead>
          <tbody>
            ${CZ.batches.map((b) => `<tr><td>${b.batchCode}</td><td>${b.name}</td><td>${b.qty}</td><td>${b.barcode}</td><td>${b.loc}</td><td>${b.expire}</td></tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  }

  function renderOutbound() {
    const batch = CZ.batches.find((b) => b.id === state.selectedBatch);
    const material = CZ.materials.find((m) => m.id === batch?.itemId);
    const used = 6;
    const limit = state.user.monthlyLimit ?? 999;
    return `
      <div class="page">
        ${pageHead('扫码领用', '填写数量、用途、项目号；系统实时校验库存与个人月度限额')}
        <div class="steps">
          <div class="step ${state.outboundStep >= 0 ? 'on' : ''}">1. 扫码选料</div>
          <div class="step ${state.outboundStep >= 1 ? 'on' : ''}">2. 填写领用</div>
          <div class="step ${state.outboundStep >= 2 ? 'on' : ''}">3. 生成领用单</div>
        </div>
        <div class="form-grid">
          <form id="outboundForm">
            <div class="field"><label>扫码/选择批次</label>
              <select id="batchSelect">
                ${CZ.batches.map((b) => `<option value="${b.id}" ${b.id === state.selectedBatch ? 'selected' : ''}>${b.barcode} · ${b.name}（库存 ${b.qty}）</option>`).join('')}
              </select>
            </div>
            <div class="form-row">
              <div class="field"><label>数量</label><input name="qty" type="number" min="1" value="1" required></div>
              <div class="field"><label>项目号</label><input name="project" placeholder="PRJ-2025-xxx" required></div>
            </div>
            <div class="field"><label>用途</label><textarea name="usage" rows="3" required>课程实验</textarea></div>
            <button class="btn btn-primary" ${state.user.role === 'guest' ? 'disabled' : ''}>确认出库</button>
          </form>
          <div>
            <div class="panel">
              <h3>实时校验</h3>
              <p>当前批次库存：${batch?.qty ?? 0}</p>
              <p>个人月度已用 / 限额：${used} / ${limit}</p>
              <p>单价：¥${material?.price ?? '-'}</p>
            </div>
            ${state.outboundStep >= 2 ? `
              <div class="panel" style="margin-top:12px">
                <h3>领用单预览</h3>
                <p>领用人：${state.user.name}</p>
                <p>材料：${batch?.name}</p>
                <p>条码：${batch?.barcode}</p>
                <div class="sign">手写签名区域</div>
              </div>` : ''}
          </div>
        </div>
      </div>`;
  }

  function renderReturnScrap() {
    return `
      <div class="page">
        ${pageHead('归还 / 报废', '支持部分归还；报废可拍照上传损耗说明')}
        <form id="returnForm" style="max-width:640px">
          <div class="field"><label>业务类型</label>
            <select name="type"><option value="RETURN">归还 RETURN</option><option value="SCRAP">报废 SCRAP</option></select>
          </div>
          <div class="field"><label>关联批次</label>
            <select name="batchId">${CZ.batches.map((b) => `<option value="${b.id}">${b.name} · ${b.batchCode}</option>`).join('')}</select>
          </div>
          <div class="field"><label>数量</label><input name="qty" type="number" min="1" value="1" required></div>
          <div class="field"><label>说明</label><textarea name="usage" rows="3" required placeholder="未开封归还 / 包装破损等"></textarea></div>
          <div class="field"><label>损耗照片</label><input type="file" accept="image/*"></div>
          <button class="btn btn-primary">提交</button>
        </form>
      </div>`;
  }

  function renderInventory() {
    return `
      <div class="page">
        ${pageHead('库存批次', '对应 stock_batch：批次号、数量、有效期、条码、货架位')}
        <table>
          <thead><tr><th>批次号</th><th>材料</th><th>条码</th><th>数量</th><th>货架位</th><th>有效期</th></tr></thead>
          <tbody>
            ${CZ.batches.map((b) => {
              const m = CZ.materials.find((x) => x.id === b.itemId);
              const low = m ? b.qty < m.safe : false;
              return `<tr><td>${b.batchCode}</td><td>${b.name}</td><td>${b.barcode}</td>
                <td><span class="tag ${low ? 'tag-bad' : 'tag-in'}">${b.qty}</span></td>
                <td>${b.loc}</td><td>${b.expire}</td></tr>`;
            }).join('')}
          </tbody>
        </table>
      </div>`;
  }

  function renderAlerts() {
    const can = ['buyer', 'admin'].includes(state.user.role);
    return `
      <div class="page">
        ${pageHead('库存预警', '低于安全库存时自动邮件 / 企业微信提醒采购员',
          `<button class="btn" id="mailBtn" ${can ? '' : 'disabled'}>邮件催办</button>
           <button class="btn btn-primary" id="wxBtn" ${can ? '' : 'disabled'}>企微催办</button>`)}
        <table>
          <thead><tr><th>材料</th><th>当前库存</th><th>安全库存</th><th>缺口</th><th>级别</th><th>最近通知</th><th>操作</th></tr></thead>
          <tbody>
            ${CZ.alerts.map((a) => `
              <tr>
                <td>${a.name}</td><td>${a.qty}</td><td>${a.safe}</td><td>${a.safe - a.qty}</td>
                <td><span class="tag ${a.level === 'danger' ? 'tag-bad' : 'tag-warn'}">${a.level === 'danger' ? '紧急' : '关注'}</span></td>
                <td>${a.at}</td>
                <td><button class="btn planBtn">生成采购计划</button></td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  }

  function renderReports() {
    const rows = CZ.txns.filter((t) => t.type === 'OUT');
    return `
      <div class="page">
        ${pageHead('统计与导出', '一键生成学期耗材汇总、经费对应表、个人领用明细（Excel + PDF）')}
        <div class="form-row" style="margin-bottom:12px">
          <div class="field"><label>学期</label><select><option>2025 春学期</option><option>2025 秋学期</option></select></div>
          <div class="field"><label>课程/项目</label><select><option>分析化学实验</option><option>分子生物学</option><option>PRJ-2025-012</option></select></div>
        </div>
        <div style="display:flex;gap:8px;margin-bottom:14px;flex-wrap:wrap">
          <button class="btn btn-primary exportBtn" data-msg="已导出《经费对应表》Excel（演示）" ${canExport() ? '' : 'disabled'}>导出经费对应表</button>
          <button class="btn exportBtn" data-msg="已导出《学期耗材汇总表》" ${canExport() ? '' : 'disabled'}>学期耗材汇总</button>
          <button class="btn exportBtn" data-msg="已导出《个人领用明细》PDF" ${canExport() ? '' : 'disabled'}>个人领用明细 PDF</button>
        </div>
        <table>
          <thead><tr><th>材料</th><th>数量</th><th>使用人</th><th>项目号</th><th>用途</th></tr></thead>
          <tbody>
            ${rows.map((t) => `<tr><td>${t.name}</td><td>${t.qty}</td><td>${t.user}</td><td>${t.project}</td><td>${t.usage}</td></tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  }

  function renderUsers() {
    return `
      <div class="page">
        ${pageHead('用户与权限', '游客仅查 · 学生领用 · 实验员出入库 · 管理员字典维护（对接 LDAP）')}
        <table>
          <thead><tr><th>LDAP UID</th><th>姓名</th><th>角色</th><th>院系/部门</th><th>邮箱</th><th>手机</th><th>月度限额</th></tr></thead>
          <tbody>
            ${CZ.users.map((u) => `
              <tr>
                <td>${u.ldapUid}</td><td>${u.name}</td><td><span class="pill">${CZ.ROLE_LABEL[u.role]}</span></td>
                <td>${u.dept}</td><td>${u.email}</td><td>${u.phone}</td><td>${u.monthlyLimit ?? '—'}</td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  }

  const pages = {
    dashboard: renderDashboard,
    categories: renderCategories,
    materials: renderMaterials,
    inbound: renderInbound,
    outbound: renderOutbound,
    returnScrap: renderReturnScrap,
    inventory: renderInventory,
    alerts: renderAlerts,
    reports: renderReports,
    users: renderUsers,
  };

  function renderShell() {
    return `
      <div class="shell">
        <aside class="sider">
          <div class="logo"><span class="brand-mark" style="width:34px;height:34px;font-size:14px">材</span>材智通</div>
          <nav class="nav">
            ${menus.map((m) => `<a href="#${m.id}" class="${state.route === m.id ? 'active' : ''}" data-route="${m.id}">${m.label}</a>`).join('')}
          </nav>
        </aside>
        <div class="main">
          <header class="topbar">
            <div class="muted">智慧材料库数字化管理系统</div>
            <div class="userbox">
              <span class="pill">演示环境 · Mock 数据</span>
              <div class="avatar">${state.user.name.slice(0, 1)}</div>
              <div>
                <div style="font-weight:600">${state.user.name}</div>
                <div class="muted" style="font-size:12px">${CZ.ROLE_LABEL[state.user.role]}</div>
              </div>
              <button class="btn" id="logoutBtn">退出</button>
            </div>
          </header>
          <div class="content">${pages[state.route]()}</div>
        </div>
      </div>`;
  }

  function bind() {
    if (!state.user) {
      document.getElementById('loginBtn')?.addEventListener('click', () => {
        const id = document.getElementById('loginUser').value;
        state.user = CZ.users.find((u) => u.id === id);
        state.route = 'dashboard';
        render();
        toast('登录成功');
      });
      return;
    }

    document.querySelectorAll('[data-route]').forEach((el) => {
      el.addEventListener('click', (e) => {
        e.preventDefault();
        state.route = el.dataset.route;
        state.outboundStep = 0;
        render();
      });
    });

    document.getElementById('logoutBtn')?.addEventListener('click', () => {
      state.user = null;
      render();
    });

    document.getElementById('addCat')?.addEventListener('click', () => toast('类目已添加（本地演示）'));

    document.getElementById('inboundForm')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const code = fd.get('barcode') || `MAT-${String(fd.get('itemId')).toUpperCase()}-001`;
      state.previewCode = code;
      toast(`入库成功，批次号已生成（演示）`);
      render();
    });

    document.getElementById('batchSelect')?.addEventListener('change', (e) => {
      state.selectedBatch = e.target.value;
      state.outboundStep = 1;
      render();
    });

    document.getElementById('outboundForm')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const qty = Number(new FormData(e.target).get('qty'));
      const batch = CZ.batches.find((b) => b.id === state.selectedBatch);
      const used = 6;
      const limit = state.user.monthlyLimit ?? 999;
      if (qty > batch.qty) return toast('超出库存，无法领用');
      if (used + qty > limit) return toast('超出个人月度限额');
      state.outboundStep = 2;
      toast('领用成功，已生成《领用单》PDF（演示）');
      render();
    });

    document.getElementById('returnForm')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const type = new FormData(e.target).get('type');
      toast(type === 'RETURN' ? '归还登记成功（演示）' : '报废登记成功（演示）');
    });

    document.getElementById('mailBtn')?.addEventListener('click', () => toast('已模拟发送邮件提醒采购员'));
    document.getElementById('wxBtn')?.addEventListener('click', () => toast('已模拟推送企业微信消息'));
    document.querySelectorAll('.planBtn').forEach((btn) => btn.addEventListener('click', () => toast('已生成采购计划草稿（演示）')));
    document.querySelectorAll('.exportBtn').forEach((btn) => btn.addEventListener('click', () => toast(btn.dataset.msg)));
  }

  function render() {
    app.innerHTML = state.user ? renderShell() : renderLogin();
    bind();
  }

  render();
})();
