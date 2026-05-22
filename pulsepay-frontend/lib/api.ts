export type TestPaymentRequest = {
  userId: string;
  amount: number;
  currency: string;
};

export type TestPaymentResponse = {
  paymentId: string;
  userId?: string;
  amount?: number;
  currency?: string;
  status: string;
  correlationId: string;
  timestamp: string;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

export async function createTestPayment(
  payload: TestPaymentRequest
): Promise<TestPaymentResponse> {
  if (!API_BASE_URL) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured.");
  }

  const response = await fetch(`${API_BASE_URL}/payments/test`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Payment request failed with ${response.status}`);
  }

  return response.json() as Promise<TestPaymentResponse>;
}
