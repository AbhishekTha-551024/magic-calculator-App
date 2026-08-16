const http = require('http');
const data = JSON.stringify({ data: { planType: 'MONTHLY' }});

const options = {
  hostname: '127.0.0.1',
  port: 5001,
  path: '/magic-calculator-dev/asia-south1/createRazorpayOrder',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(data)
  }
};

const req = http.request(options, res => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => {
    console.log('STATUS', res.statusCode);
    try {
      console.log(JSON.stringify(JSON.parse(body), null, 2));
    } catch (e) {
      console.log(body);
    }
  });
});

req.on('error', err => console.error('ERROR', err));
req.write(data);
req.end();
