package com.example.blescanner;

public class ModelData {

    private String name, created_at, updated_at;
    private int location_id;

    public int getLocation_id(){
        return location_id;
    }

    public void setLocation_id(int location_id){
        this.location_id = location_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}