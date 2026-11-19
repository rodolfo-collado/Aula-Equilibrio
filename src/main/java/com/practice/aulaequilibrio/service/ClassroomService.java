package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.ClassroomDAO;
import com.practice.aulaequilibrio.dao.ClassroomTypeDAO;
import com.practice.aulaequilibrio.model.Classroom;
import com.practice.aulaequilibrio.model.ClassroomType;
import com.practice.aulaequilibrio.model.ClassroomStatus;
import com.practice.aulaequilibrio.util.Validations;
import lombok.RequiredArgsConstructor;
import com.practice.aulaequilibrio.model.Permission;

import java.util.List;

@RequiredArgsConstructor
public class ClassroomService {
    private final ClassroomDAO classrooms;
    private final ClassroomTypeDAO types;
    private final AccessControl access;

    public ClassroomService() {
        this(new ClassroomDAO(), new ClassroomTypeDAO(), AccessControl.current());
    }

    public List<Classroom> listClassrooms(Integer typeId) {
        access.require(Permission.READ_ACADEMIC);
        return typeId == null ? classrooms.listar() : classrooms.listarPorTipo(typeId);
    }

    public List<ClassroomType> listTypes() {
        access.require(Permission.READ_ACADEMIC);
        return types.listar();
    }

    public void saveClassroom(Classroom classroom) {
        access.require(Permission.EDIT_CLASSROOMS);
        classrooms.guardar(classroom);
    }

    public void saveClassroom(Integer id, long revision, String code, int capacity, Integer typeId, ClassroomStatus state) {
        access.require(Permission.EDIT_CLASSROOMS);
        classrooms.save(id, revision, Validations.required(code, "código", 20), capacity, typeId, state);
    }

    public void saveType(ClassroomType type) {
        access.require(Permission.EDIT_CLASSROOMS);
        types.guardar(type);
    }

    public void saveType(Integer id, long revision, String name, String description) {
        access.require(Permission.EDIT_CLASSROOMS);
        types.save(id, revision, Validations.required(name, "nombre", 60), description);
    }

    public void deleteClassroom(Classroom classroom) {
        access.require(Permission.EDIT_CLASSROOMS);
        classrooms.eliminar(classroom);
    }

    public void deleteType(ClassroomType type) {
        access.require(Permission.EDIT_CLASSROOMS);
        types.eliminar(type);
    }
}
