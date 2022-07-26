long fibonacci(int n) {
    long a = 1;
    long b = 1;
    for (int i = 0; i < n; i++) {
        long c = a + b;
        a = b;
        b = c;
    }
    return a;
}

long main(int loop_count) {
    long r = fibonacci(loop_count);
    return r;
}