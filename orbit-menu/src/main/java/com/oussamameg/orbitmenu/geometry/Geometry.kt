package com.oussamameg.orbitmenu.geometry

open class Geometry {
    val vertices: MutableList<Vertex> = mutableListOf()
    val faces: MutableList<Face> = mutableListOf()

    fun addVertex(vararg coords: Float): Geometry {
        for (i in coords.indices step 3) {
            vertices.add(Vertex(coords[i], coords[i + 1], coords[i + 2]))
        }
        return this
    }

    fun addFace(vararg indices: Int): Geometry {
        for (i in indices.indices step 3) {
            faces.add(Face(indices[i], indices[i + 1], indices[i + 2]))
        }
        return this
    }

    val lastVertex: Vertex
        get() = vertices.last()

    fun subdivide(divisions: Int = 1): Geometry {
        val midPointCache = mutableMapOf<String, Int>()
        var currentFaces = faces

        repeat(divisions) {
            val newFaces = ArrayList<Face>(currentFaces.size * 4)

            currentFaces.forEachIndexed { ndx, face ->
                val mAB = getMidPoint(face.a, face.b, midPointCache)
                val mBC = getMidPoint(face.b, face.c, midPointCache)
                val mCA = getMidPoint(face.c, face.a, midPointCache)

                val i = ndx * 4
                newFaces.add(Face(face.a, mAB, mCA))
                newFaces.add(Face(face.b, mBC, mAB))
                newFaces.add(Face(face.c, mCA, mBC))
                newFaces.add(Face(mAB, mBC, mCA))
            }

            currentFaces = newFaces
        }

        faces.clear()
        faces.addAll(currentFaces)
        return this
    }

    fun spherize(radius: Float = 1f): Geometry {
        vertices.forEach { vertex ->
            vertex.normal.set(vertex.position).normalize()
            vertex.position.set(vertex.normal).mul(radius)
        }
        return this
    }

    data class GeometryData(
        val vertices: FloatArray,
        val indices: ShortArray,
        val normals: FloatArray,
        val uvs: FloatArray
    )

    val data: GeometryData
        get() = GeometryData(
            vertexData,
            indexData,
            normalData,
            uvData
        )

    val vertexData: FloatArray
        get() = vertices.flatMap { listOf(it.position.x, it.position.y, it.position.z) }
            .toFloatArray()

    val normalData: FloatArray
        get() = vertices.flatMap { listOf(it.normal.x, it.normal.y, it.normal.z) }
            .toFloatArray()

    val uvData: FloatArray
        get() = vertices.flatMap { listOf(it.uv.x, it.uv.y) }
            .toFloatArray()

    val indexData: ShortArray
        get() = faces.flatMap { listOf(it.a, it.b, it.c) }
            .map { it.toShort() }
            .toShortArray()

    private fun getMidPoint(
        ndxA: Int,
        ndxB: Int,
        cache: MutableMap<String, Int>
    ): Int {
        val cacheKey = if (ndxA < ndxB) "k_${ndxB}_${ndxA}" else "k_${ndxA}_${ndxB}"

        cache[cacheKey]?.let { return it }

        val a = vertices[ndxA].position
        val b = vertices[ndxB].position
        val ndx = vertices.size

        cache[cacheKey] = ndx
        addVertex(
            (a.x + b.x) * 0.5f,
            (a.y + b.y) * 0.5f,
            (a.z + b.z) * 0.5f
        )

        return ndx
    }
}