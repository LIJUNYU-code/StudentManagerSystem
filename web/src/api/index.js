// 业务 API 集中声明：每个函数 = 一个后端端点。
// 页面里不直接写 URL 字符串，统一 import 这里——后端改路径时只改一个文件。
import request from '@/api/request'

// ---------- 学生 ----------
export const studentLogin = (data) => request.post('/student/login', data)
export const listStudents = (params) => request.get('/student', { params }) // params: {keyword,page,size}
export const addStudent = (data) => request.post('/student/register', data)
export const updateStudent = (id, data) => request.put(`/student/${id}`, data)
export const deleteStudent = (id) => request.delete(`/student/${id}`)

// ---------- 班级 ----------
export const listClazzes = (params) => request.get('/clazz', { params })
export const addClazz = (data) => request.post('/clazz', data)
export const updateClazz = (id, data) => request.put(`/clazz/${id}`, data)
export const deleteClazz = (id) => request.delete(`/clazz/${id}`)

// ---------- 课程 ----------
export const listCourses = (params) => request.get('/course', { params })
export const addCourse = (data) => request.post('/course', data)
export const updateCourse = (id, data) => request.put(`/course/${id}`, data)
export const deleteCourse = (id) => request.delete(`/course/${id}`)

// ---------- 选课 ----------
export const listEnrollments = (params) => request.get('/enrollments', { params }) // {courseId,page,pageSize}
export const deleteEnrollment = (id) => request.delete(`/enrollments/${id}`)

// ---------- 认证（教师/管理员） ----------
export const authRegister = (data) => request.post('/auth/register', data)
// 登出：服务端把当前 token 写进 Redis 黑名单（TTL=剩余有效期），前端同时清本地状态
export const authLogout = () => request.post('/auth/logout')
// 修改密码：后端校验旧密码、更新 pwd_change_time（旧 token 全部下线）、重新签发 token
export const changePassword = (data) => request.post('/auth/password', data) // {oldPassword,newPassword}

// ---------- 管理员：用户审核 ----------
export const listAdminUsers = (params) => request.get('/admin/user', { params }) // {keyword,status,page,pageSize}
export const approveUser = (id) => request.put(`/admin/user/${id}/approve`)
export const disableUser = (id) => request.put(`/admin/user/${id}/disable`)

// ---------- 选课 ----------
export const addEnrollment = (data) => request.post('/enrollments', data) // {studentId,courseId}

// ---------- 成绩 ----------
export const listScores = (params) => request.get('/scores', { params }) // {courseId,page,pageSize}
export const addScore = (data) => request.post('/scores', data)
export const listScoresMy = () => request.get('/scores/my') // 学生查自己的成绩（后端从 token 取身份）
