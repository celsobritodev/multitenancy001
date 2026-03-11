const fs = require('fs');
const input = process.argv[2], output = process.argv[3];
const collection = JSON.parse(fs.readFileSync(input,'utf8'));
function ensureEvent(item){ if(!Array.isArray(item.event)) item.event=[]; return item.event; }
function upsertScript(item, listen, lines){ const evs=ensureEvent(item); let ev=evs.find(e=>e&&e.listen===listen); if(!ev){ ev={listen,script:{type:'text/javascript',exec:[]}}; evs.push(ev);} ev.script={type:'text/javascript',exec:lines}; }
function walk(items, fn){ for(const item of items||[]){ fn(item); if(Array.isArray(item.item)) walk(item.item, fn); } }
function findGroup(items, regex){ for(const item of items||[]){ if(regex.test(item.name||'')) return item; if(Array.isArray(item.item)){ const f=findGroup(item.item, regex); if(f) return f; } } return null; }
function findFirst(items, regex){ for(const item of items||[]){ if(regex.test(item.name||'')) return item; if(Array.isArray(item.item)){ const f=findFirst(item.item, regex); if(f) return f; } } return null; }
function findFirstIndex(items, regex){ for(let i=0;i<(items||[]).length;i++) if(regex.test(items[i].name||'')) return i; return -1; }
function clone(o){ return JSON.parse(JSON.stringify(o)); }

const massGroup=findGroup(collection.item,/99 - POPULATE MASS DATA/i);
if(!massGroup||!Array.isArray(massGroup.item)) throw new Error('Mass data group not found');

const workingTemplate = findFirst(massGroup.item,/99\.05\.0[1-9] - Criar usuario \([1-9]\/10\)|99\.05\.10 - Criar usuario \(10\/10\)/) || findFirst(massGroup.item,/99\.04\.01 - Criar produto/);
if(!workingTemplate || !workingTemplate.request) throw new Error('Working tenant request template not found');

function makeCustomerItem(idx){
  const base=[
    ['Joao Silva','11999999999','Rua Teste 123','01000-000','Mass customer 1'],
    ['Maria Oliveira','11888888888','Avenida Teste 456','02000-000','Mass customer 2'],
    ['Carlos Souza','11777777777','Rua Alfa 789','03000-000','Mass customer 3'],
    ['Ana Lima','11666666666','Rua Beta 321','04000-000','Mass customer 4'],
    ['Pedro Santos','11555555555','Rua Gama 654','05000-000','Mass customer 5'],
    ['Lucia Ferreira','11444444444','Rua Delta 987','06000-000','Mass customer 6'],
    ['Marcos Costa','11333333333','Rua Epsilon 159','07000-000','Mass customer 7'],
    ['Fernanda Alves','11222222222','Rua Zeta 753','08000-000','Mass customer 8'],
    ['Rafael Gomes','11111111111','Rua Eta 852','09000-000','Mass customer 9'],
    ['Patricia Rocha','11912345678','Rua Theta 951','10000-000','Mass customer 10'],
  ][idx-1];

  const request = clone(workingTemplate.request);
  request.method = 'POST';
  request.url = { raw:'{{base_url}}/api/tenant/customers', host:['{{base_url}}'], path:['api','tenant','customers'] };
  request.body = { mode:'raw', raw:'{}' };
  request.header = Array.isArray(request.header) ? request.header.filter(h => !(h && h.key && h.key.toLowerCase()==='content-type')) : [];
  request.header.push({key:'Content-Type', value:'application/json', type:'text'});

  return {
    name:`99.05.${String(idx+10).padStart(2,'0')} - Criar customer (${idx}/10)`,
    request,
    response:[],
    event:[
      {listen:'prerequest',script:{type:'text/javascript',exec:[
        `const idx = ${idx};`,`const base = ${JSON.stringify(base)};`,"const ts = Date.now();","const safeName = `${base[0]} ${ts}`;","const safeEmail = `${base[0].toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/\\s+/g,'.')}.${ts}@email.com`;","const payload = { name:safeName,email:safeEmail,phone:base[1],document:String(ts).slice(-9)+String(10+idx).padStart(2,'0'),documentType:'CPF',address:base[2],city:'Sao Paulo',state:'SP',zipCode:base[3],country:'Brasil',notes:base[4] };","pm.variables.set('customer_name', payload.name);","pm.variables.set('customer_email', payload.email);","pm.request.body.update(JSON.stringify(payload,null,2));","console.log('==================================================');","console.log('👤 Customer payload:', JSON.stringify(payload,null,2));","console.log('📊 customer_name:', payload.name);","console.log('📊 customer_email:', payload.email);","console.log('==================================================');"
      ]}},
      {listen:'test',script:{type:'text/javascript',exec:[
        "pm.test('status 2xx/201', function(){ pm.expect(pm.response.code).to.be.oneOf([200,201,204]); });","let res={}; try{ res=pm.response.json(); }catch(e){}","const created = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]');","if (res && res.id) { created.push(res.id); pm.collectionVariables.set('created_customer_ids', JSON.stringify(created)); }","console.log('✅ Customer criado com ID:', res && res.id);","console.log('🧾 Total de customers criados:', created.length);"
      ]}}
    ]
  };
}

const alreadyExists=massGroup.item.some(i=>/Criar customer \(\d+\/10\)/.test(i.name||''));
if(!alreadyExists){
  const firstSaleIndex=findFirstIndex(massGroup.item,/99\.06\.\d+ - Criar venda \(\d+\/10\)/);
  if(firstSaleIndex<0) throw new Error('First sale request not found');
  const newCustomers=[]; for(let i=1;i<=10;i++) newCustomers.push(makeCustomerItem(i));
  massGroup.item.splice(firstSaleIndex,0,...newCustomers);
}

walk(collection.item, item=>{
  const name=item.name||'';
  if(name==='99.07 - Verificar contagem final'){
    upsertScript(item,'test',[
      "pm.test('reset ok', function(){ pm.expect(pm.response.code).to.be.oneOf([200,201,204]); });","const cat = JSON.parse(pm.collectionVariables.get('created_category_ids') || '[]').length;","const sub = JSON.parse(pm.collectionVariables.get('created_subcategory_ids') || '[]').length;","const sup = JSON.parse(pm.collectionVariables.get('created_supplier_ids') || '[]').length;","const prod = JSON.parse(pm.collectionVariables.get('created_product_ids') || '[]').length;","const usr = JSON.parse(pm.collectionVariables.get('created_user_ids') || '[]').length;","const cus = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]').length;","const sal = JSON.parse(pm.collectionVariables.get('created_sale_ids') || '[]').length;","console.log('\\n📊 ====== RESUMO DA MASSA DE DADOS ======');","console.log('✅ Categorias criadas:', cat);","console.log('✅ Subcategorias criadas:', sub);","console.log('✅ Fornecedores criados:', sup);","console.log('✅ Produtos criados:', prod);","console.log('✅ Usuários criados:', usr);","console.log('✅ Customers criados:', cus);","console.log('✅ Vendas criadas:', sal);","console.log('==========================================\\n');","pm.test('Categorias: pelo menos 10 criadas', function(){ pm.expect(cat).to.be.at.least(10); });","pm.test('Subcategorias: pelo menos 10 criadas', function(){ pm.expect(sub).to.be.at.least(10); });","pm.test('Fornecedores: pelo menos 10 criados', function(){ pm.expect(sup).to.be.at.least(10); });","pm.test('Produtos: pelo menos 10 criados', function(){ pm.expect(prod).to.be.at.least(10); });","pm.test('Usuários: pelo menos 10 criados', function(){ pm.expect(usr).to.be.at.least(10); });","pm.test('Customers: pelo menos 10 criados', function(){ pm.expect(cus).to.be.at.least(10); });","pm.test('Vendas: criadas', function(){ pm.expect(sal).to.be.at.least(10); });","console.log('📊 Status da execução:');","console.log('- Esta execução adicionou MAIS 10 a cada contador');","console.log('- Total acumulado: 10 usuários (1 execuções completas)');"
    ]);
  }
  if(/99\.06\.\d+ - Criar venda \((\d+)\/10\)/.test(name)){
    const idx=Number(name.match(/\((\d+)\/10\)/)[1]);
    upsertScript(item,'prerequest',[
      "const createdCustomers = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]');","const createdProducts = JSON.parse(pm.collectionVariables.get('created_product_ids') || '[]');",`const idx = ${idx};`,"if (createdCustomers.length < 10) { throw new Error('created_customer_ids has less than 10 items'); }","if (createdProducts.length < 2) { throw new Error('created_product_ids has less than 2 items'); }","const customerId = createdCustomers[idx - 1] || createdCustomers[0];","const payload = { customerId, saleDate:new Date().toISOString(), status:'DRAFT', items:[ { productId: createdProducts[0], productName: `Product ${createdProducts[0]}`, quantity:1, unitPrice:100 }, { productId: createdProducts[1], productName: `Product ${createdProducts[1]}`, quantity:2, unitPrice:100 } ] };","pm.request.body.update(JSON.stringify(payload,null,2));","console.log('==================================================');","console.log('📦 Body enviado:', JSON.stringify(payload,null,2));","console.log('📊 customerId:', payload.customerId);","console.log('📊 items:', JSON.stringify(payload.items,null,2));","console.log('📊 saleDate:', payload.saleDate);","console.log('📊 status:', payload.status);","console.log('==================================================');"
    ]);
    upsertScript(item,'test',[
      "pm.test('status 2xx/201', function(){ pm.expect(pm.response.code).to.be.oneOf([200,201,204]); });","let res={}; try{ res=pm.response.json(); }catch(e){}","const created = JSON.parse(pm.collectionVariables.get('created_sale_ids') || '[]');","if (res && res.id) { created.push(res.id); pm.collectionVariables.set('created_sale_ids', JSON.stringify(created)); }","console.log('✅ Venda criada com ID:', res && res.id);","console.log('🧾 Total de vendas criadas:', created.length);"
    ]);
  }
});

fs.writeFileSync(output, JSON.stringify(collection,null,2));
