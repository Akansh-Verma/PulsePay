# PulsePay Frontend

Minimal Next.js 15 frontend for triggering the PulsePay `payment-service` test endpoint.

## Stack

- Next.js 15 App Router
- TypeScript
- Tailwind CSS
- shadcn/ui-style components
- Fetch API

## Setup

```bash
npm install
cp .env.example .env.local
npm run dev
```

Set the backend URL in `.env.local`:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8082
```

Open [http://localhost:3000](http://localhost:3000).

## Backend Contract

The dashboard sends:

```http
POST /payments/test
```

to:

```text
${NEXT_PUBLIC_API_BASE_URL}/payments/test
```

with a JSON body:

```json
{
  "userId": "user_test_001",
  "amount": 99.99,
  "currency": "USD"
}
```

The current backend test endpoint generates its own dummy event values and returns:

- `paymentId`
- `status`
- `correlationId`
- `timestamp`

## Vercel Deployment

Deploy the `pulsepay-frontend` folder as the Vercel project root.

Add this environment variable in Vercel:

```bash
NEXT_PUBLIC_API_BASE_URL=https://your-payment-service-host
```

Then run the default Vercel build command:

```bash
npm run build
```

## Notes

This frontend is intentionally lightweight. It does not include authentication, Redux, persistence, charts, analytics, websockets, or theme toggles.
