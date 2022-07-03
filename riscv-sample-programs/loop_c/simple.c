int func(){
    int s = 0;
    for (int i = 1; i <= 5; i++){
        s += i;
        s += i;
    }
    return s;
}

int main(){
    int r = func();
    return r;
}