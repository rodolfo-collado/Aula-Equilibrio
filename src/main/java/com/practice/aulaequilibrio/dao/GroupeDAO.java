package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.model.Groupe;

import java.util.List;

public class GroupeDAO extends IDAO<Groupe> {

    public GroupeDAO() {
        super(Groupe.class, Groupe::getId);
    }

    public List<Groupe> findAll() {
        return findAll("nombre");
    }

    public boolean existsByNombre(String nombre, Integer excludeId) {
        return existsByFieldIgnoreCase("nombre", nombre, excludeId);
    }
}
