'use client';

import { QueryClientProvider } from '@tanstack/react-query';

import Header from '@/components/common/Header/Header';

import client from '@/api/client';

export default function GardenLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <QueryClientProvider client={client}>
      <Header />
      <div className="flex flex-col items-center h-full bg-cover bg-center bg-no-repeat bg-[url('/assets/img/bg_default.png')]">
        {children}
      </div>
    </QueryClientProvider>
  );
}
