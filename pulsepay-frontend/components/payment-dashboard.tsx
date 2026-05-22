"use client";

import { FormEvent, useMemo, useState } from "react";
import { Activity, ArrowRight, CheckCircle2, Loader2, RadioTower } from "lucide-react";
import { createTestPayment, type TestPaymentResponse } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select";

const currencies = ["USD", "INR", "EUR", "GBP"];

export function PaymentDashboard() {
  const [userId, setUserId] = useState("user_test_001");
  const [amount, setAmount] = useState("99.99");
  const [currency, setCurrency] = useState("USD");
  const [payment, setPayment] = useState<TestPaymentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const apiBaseUrl = useMemo(
    () => process.env.NEXT_PUBLIC_API_BASE_URL ?? "Not configured",
    []
  );

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    setPayment(null);

    try {
      const response = await createTestPayment({
        userId: userId.trim(),
        amount: Number(amount),
        currency
      });
      setPayment(response);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to create a test payment."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(20,184,166,0.16),_transparent_32rem),linear-gradient(135deg,_#f8fafc_0%,_#eef6f3_48%,_#f8fafc_100%)]">
      <section className="mx-auto flex min-h-screen w-full max-w-6xl flex-col px-5 py-6 sm:px-8 lg:px-10">
        <header className="flex flex-col gap-4 border-b border-border/80 pb-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-md bg-primary text-primary-foreground shadow-sm">
                <RadioTower className="h-5 w-5" />
              </div>
              <h1 className="text-3xl font-semibold tracking-tight text-slate-950">
                PulsePay
              </h1>
            </div>
            <p className="mt-2 text-sm text-muted-foreground">
              Event-driven payment test console
            </p>
          </div>
          <Badge className="w-fit border-emerald-200 bg-emerald-50 text-emerald-700">
            Payment service MVP
          </Badge>
        </header>

        <div className="grid flex-1 gap-6 py-8 lg:grid-cols-[0.9fr_1.1fr] lg:items-start">
          <aside className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>Architecture Status</CardTitle>
                <CardDescription>
                  Frontend trigger layer connected to the payment-service API.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <StatusRow
                  label="Client"
                  value="Next.js App Router"
                  tone="ready"
                />
                <StatusRow
                  label="API base"
                  value={apiBaseUrl}
                  tone={apiBaseUrl === "Not configured" ? "warning" : "ready"}
                />
                <StatusRow
                  label="Endpoint"
                  value="POST /payments/test"
                  tone="ready"
                />
              </CardContent>
            </Card>

            <Card className="border-teal-200/80 bg-white/85">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Activity className="h-5 w-5 text-primary" />
                  Event Flow
                </CardTitle>
              </CardHeader>
              <CardContent className="grid gap-3 text-sm text-muted-foreground">
                <FlowStep label="Dashboard" />
                <FlowStep label="Payment Service" />
                <FlowStep label="Kafka payment.events" />
              </CardContent>
            </Card>
          </aside>

          <section className="grid gap-6">
            <Card>
              <CardHeader>
                <CardTitle>Create Test Payment</CardTitle>
                <CardDescription>
                  Submit a lightweight payment event request to the backend.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <form className="grid gap-5" onSubmit={handleSubmit}>
                  <div className="grid gap-2">
                    <Label htmlFor="userId">userId</Label>
                    <Input
                      id="userId"
                      value={userId}
                      onChange={(event) => setUserId(event.target.value)}
                      placeholder="user_test_001"
                      required
                    />
                  </div>

                  <div className="grid gap-2">
                    <Label htmlFor="amount">amount</Label>
                    <Input
                      id="amount"
                      min="0"
                      step="0.01"
                      type="number"
                      value={amount}
                      onChange={(event) => setAmount(event.target.value)}
                      placeholder="99.99"
                      required
                    />
                  </div>

                  <div className="grid gap-2">
                    <Label htmlFor="currency">currency</Label>
                    <Select value={currency} onValueChange={setCurrency}>
                      <SelectTrigger id="currency">
                        <SelectValue placeholder="Select currency" />
                      </SelectTrigger>
                      <SelectContent>
                        {currencies.map((currencyCode) => (
                          <SelectItem key={currencyCode} value={currencyCode}>
                            {currencyCode}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <Button
                    className="mt-1 w-full sm:w-fit"
                    disabled={isSubmitting}
                    size="lg"
                    type="submit"
                  >
                    {isSubmitting ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <ArrowRight className="h-4 w-4" />
                    )}
                    Create Test Payment
                  </Button>
                </form>
              </CardContent>
            </Card>

            {error ? (
              <Card className="border-red-200 bg-red-50">
                <CardContent className="pt-6 text-sm font-medium text-red-700">
                  {error}
                </CardContent>
              </Card>
            ) : null}

            {payment ? <PaymentResult payment={payment} /> : null}
          </section>
        </div>
      </section>
    </main>
  );
}

function StatusRow({
  label,
  value,
  tone
}: {
  label: string;
  value: string;
  tone: "ready" | "warning";
}) {
  return (
    <div className="flex items-start justify-between gap-4 rounded-md border border-border bg-white px-4 py-3">
      <div>
        <p className="text-xs font-medium uppercase text-muted-foreground">
          {label}
        </p>
        <p className="mt-1 break-all text-sm font-medium">{value}</p>
      </div>
      <span
        className={
          tone === "ready"
            ? "mt-1 h-2.5 w-2.5 rounded-full bg-emerald-500"
            : "mt-1 h-2.5 w-2.5 rounded-full bg-amber-500"
        }
      />
    </div>
  );
}

function FlowStep({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-3">
      <CheckCircle2 className="h-4 w-4 flex-none text-primary" />
      <span>{label}</span>
    </div>
  );
}

function PaymentResult({ payment }: { payment: TestPaymentResponse }) {
  const fields = [
    ["paymentId", payment.paymentId],
    ["status", payment.status],
    ["correlationId", payment.correlationId],
    ["timestamp", payment.timestamp]
  ];

  return (
    <Card className="border-emerald-200 bg-emerald-50/80">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-emerald-800">
          <CheckCircle2 className="h-5 w-5" />
          Payment Created
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3">
        {fields.map(([label, value]) => (
          <div
            className="grid gap-1 rounded-md border border-emerald-200 bg-white px-4 py-3 sm:grid-cols-[9rem_1fr] sm:items-center"
            key={label}
          >
            <span className="text-xs font-semibold uppercase text-emerald-700">
              {label}
            </span>
            <span className="break-all font-mono text-sm text-slate-900">
              {value}
            </span>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
