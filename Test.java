package com.example.coverscreenmirror;
import rikka.shizuku.Shizuku;
public class Test {
    public static void main(String[] args) {
        java.lang.reflect.Method[] methods = Shizuku.class.getDeclaredMethods();
        for(java.lang.reflect.Method m : methods) {
            System.out.println(m.getName());
        }
    }
}
