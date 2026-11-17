package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.ClassroomDAO;
import com.practice.aulaequilibrio.dao.ClassroomTypeDAO;
import com.practice.aulaequilibrio.model.Classroom;
import com.practice.aulaequilibrio.model.ClassroomType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ClassroomService {
    private final ClassroomDAO classrooms;
    private final ClassroomTypeDAO types;

    public ClassroomService() {
        this(new ClassroomDAO(), new ClassroomTypeDAO());
    }

    public List<Classroom> listClassrooms(Integer typeId) {
        return typeId == null ? classrooms.listar() : classrooms.listarPorTipo(typeId);
    }

    public List<ClassroomType> listTypes() {
        return types.listar();
    }

    public void saveClassroom(Classroom classroom) {
        classrooms.guardar(classroom);
    }

    public void saveType(ClassroomType type) {
        types.guardar(type);
    }

    public void deleteClassroom(Classroom classroom) {
        classrooms.eliminar(classroom);
    }

    public void deleteType(ClassroomType type) {
        types.eliminar(type);
    }
}
