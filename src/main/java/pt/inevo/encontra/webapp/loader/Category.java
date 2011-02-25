package pt.inevo.encontra.webapp.loader;

public class Category {

    private String category;

    public void setCategory(String category){
        this.category = category;
    }

    public String getCategory(){
        return this.category;
    }

    public String toString(){
        return "{Category: " + category + "}";
    }
}
