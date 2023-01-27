long fibonacci(long n){
    long a = 1;
    long b = 1;
    for (long i = 0; i < n;i++){
        long c = a + b;
        a = b;
        b = c;
    }
    return a;
}

long main(long loop_count){
    long r = fibonacci(loop_count);
    return r;
}