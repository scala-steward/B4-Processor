int fibonacci(int n){
    int a = 1;
    int b = 1;
    for (int i = 0; i < n;i++){
        int c = a+b;
        a = b;
        b = c;
    }
    return a;
}

int main(){
    return fibonacci(4);
}