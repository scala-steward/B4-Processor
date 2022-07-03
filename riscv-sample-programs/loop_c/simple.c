int func(){
    int s = 0;
    for (int i = 0; i < 10; i++){
        s += i;
    }
    return s;
}

int main(){
    int r = func();
    return r;
}