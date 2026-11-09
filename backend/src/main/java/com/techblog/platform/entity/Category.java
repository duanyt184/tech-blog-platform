package com.techblog.platform.entity;
import jakarta.persistence.*;
@Entity
@Table(name="categories")
public class Category {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, unique=true, length=40) private String name;
 @Column(nullable=false, length=300) private String description = "";
 @Column(nullable=false, length=7) private String color = "#3b82f6";
 @Column(name="parent_id") private Long parentId;
 public Long getParentId(){return parentId;}
 public void setParentId(Long value){parentId=value;}
 public Long getId(){return id;}
 public String getName(){return name;}
 public void setName(String v){name=v;}
 public String getDescription(){return description;}
 public void setDescription(String v){description=v;}
 public String getColor(){return color;}
 public void setColor(String v){color=v;}
}
