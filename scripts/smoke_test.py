#!/usr/bin/env python3
"""Exercise the JSON API against a disposable copy of sql/init.sql, never a real database.
BASE_URL=http://127.0.0.1:18080 python3 scripts/smoke_test.py
Only Python's standard library is required. The script creates and changes data.
"""
import http.cookiejar
import json
import os
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get('BASE_URL', 'http://127.0.0.1:18080').rstrip('/')
class Client:
    def __init__(self):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=None
        self.refresh_csrf()
    def call(self, path, method='GET', data=None, csrf=True, form=False):
        headers={}
        if method != 'GET' and csrf: headers[self.csrf['headerName']]=self.csrf['token']
        if data is not None:
            headers['Content-Type']='application/x-www-form-urlencoded' if form else 'application/json'
            data=urllib.parse.urlencode(data).encode() if form else json.dumps(data,ensure_ascii=False).encode()
        req=urllib.request.Request(BASE+'/api'+path,data=data,headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=20) as r: status,body=r.status,r.read().decode()
        except urllib.error.HTTPError as e: status,body=e.code,e.read().decode()
        try: body=json.loads(body) if body else None
        except json.JSONDecodeError: pass
        return status,body
    def refresh_csrf(self):
        code,body=self.call('/csrf'); assert code == 200; self.csrf=body
    def login(self,name):
        code,body=self.call('/login','POST',{'username':name,'password':'123456'},form=True); assert code == 204,(code,body); self.refresh_csrf()
    def ok(self,path,method='GET',data=None):
        code,body=self.call(path,method,data); assert 200 <= code < 300,(path,code,body); return body
    def fail(self,path,method,data,expected=None):
        code,body=self.call(path,method,data); assert code >= 400,(path,code,body)
        if expected: assert expected in body.get('message',''),(path,body)
        return code

def row(client,entity,field,value):
    items=client.ok('/admin/'+entity)['data']['rows']
    return next(x for x in items if str(x[field])==str(value))
def main():
    guest=Client()
    assert guest.ok('/statistics')['beds']['total']==2
    assert set(guest.ok('/statistics'))=={'beds','repairs'}
    assert guest.call('/my/statistics')[0]==401
    assert guest.call('/admin/visitors')[0]==401
    registration={'username':'registered01','password':'123456','name':'注册学生','student_no':'REGISTER001','class_name':'计应2401','phone':'13800138000','role':'ADMIN'}
    guest.ok('/register','POST',registration)
    guest.fail('/register','POST',{**registration,'username':'rollback01'},'重复')
    registered=Client(); registered.login('registered01')
    assert registered.ok('/session')['admin'] is False
    assert registered.ok('/session')['studentLinked'] is True
    assert registered.ok('/my/statistics')['repairs']['total']==0
    assert registered.ok('/my/statistics')['housing'] is None
    assert registered.ok('/profile')['class_name']=='计应2401'
    registered.ok('/profile','PUT',{'name':'注册学生','class_name':'计应2402','phone':'13900139000'})
    assert registered.ok('/profile')['phone']=='13900139000'
    assert registered.call('/admin/visitors')[0]==403
    print('PASS 公共统计、学生注册、强制学生角色、重复学号拒绝')
    a,s=Client(),Client(); a.login('admin'); s.login('student01')
    assert not any(x['username']=='rollback01' for x in a.ok('/admin/users')['data']['rows'])
    registered_sid=row(a,'students','student_no','REGISTER001')['id']
    registered_uid=row(a,'users','username','registered01')['id']
    visitor={'name':'来访家长','student_id':str(registered_sid),'arrived_at':'2026-11-06T10:00','left_at':''}
    a.ok('/admin/visitors','POST',visitor)
    vid=row(a,'visitors','name','来访家长')['id']
    a.fail(f'/admin/visitors/{vid}','PUT',{**visitor,'left_at':'2026-11-06T09:00'},'不能早于')
    a.ok(f'/admin/visitors/{vid}','PUT',{**visitor,'left_at':'2026-11-06T11:00'})
    assert a.ok(f'/admin/visitors/{vid}')['left_at'] is not None
    a.ok(f'/admin/visitors/{vid}','DELETE')
    a.ok(f'/admin/students/{registered_sid}','DELETE'); a.ok(f'/admin/users/{registered_uid}','DELETE')
    print('PASS 注册失败事务回滚、访客增删改查与时间校验')
    assert a.ok('/session')['admin'] and a.ok('/session')['studentLinked']
    assert not s.ok('/session')['admin'] and s.ok('/session')['studentLinked']
    assert s.call('/admin/users')[0] == 403
    assert a.call('/admin/dormitories','POST',{'building':'x','room_no':'x'},csrf=False)[0] == 403
    assert all('password_hash' not in row for row in a.ok('/admin/users')['data']['rows'])
    print('PASS 登录、角色隔离、CSRF、密码哈希不下发')
    a.ok('/admin/users','POST',{'username':'unlinked01','password':'123456','role':'STUDENT'})
    unlinked_id=row(a,'users','username','unlinked01')['id']
    unlinked=Client(); unlinked.login('unlinked01')
    assert unlinked.ok('/session')['studentLinked'] is False
    assert unlinked.call('/repairs')[0] == 400
    a.ok('/admin/students','POST',{'user_id':str(unlinked_id),'student_no':'TEST003','name':'测试三'})
    unlinked_sid=row(a,'students','student_no','TEST003')['id']
    assert unlinked.ok('/session')['studentLinked'] is True
    a.ok(f'/admin/students/{unlinked_sid}','DELETE'); a.ok(f'/admin/users/{unlinked_id}','DELETE')
    print('PASS 未建档账号引导数据与建档后恢复')
    a.ok('/admin/users','POST',{'username':'teststudent02','password':'123456','role':'STUDENT'})
    uid=row(a,'users','username','teststudent02')['id']
    a.ok(f'/admin/users/{uid}','PUT',{'username':'teststudent02','password':'','role':'STUDENT'})
    a.ok('/admin/students','POST',{'user_id':str(uid),'student_no':'TEST002','name':'测试二'})
    sid=row(a,'students','student_no','TEST002')['id']
    a.fail(f'/admin/users/{uid}','DELETE',None,'学生档案')
    a.ok(f'/admin/students/{sid}','PUT',{'user_id':str(uid),'student_no':'TEST002','name':'测试二改'})
    a.ok('/admin/dormitories','POST',{'building':'测试楼','room_no':'999'})
    did=row(a,'dormitories','building','测试楼')['id']
    a.ok(f'/admin/dormitories/{did}','PUT',{'building':'测试楼','room_no':'998'})
    a.ok('/admin/beds','POST',{'dormitory_id':str(did),'bed_no':'T1'})
    bid=row(a,'beds','bed_no','T1')['id']
    a.ok(f'/admin/beds/{bid}','PUT',{'dormitory_id':str(did),'bed_no':'T2'})
    a.fail(f'/admin/dormitories/{did}','DELETE',None,'床位')
    a.ok(f'/admin/beds/{bid}','DELETE'); a.ok(f'/admin/dormitories/{did}','DELETE')
    for i in range(11): a.ok('/admin/dormitories','POST',{'building':'分页楼','room_no':f'{i:03d}'})
    assert a.ok('/admin/dormitories')['data']['pages'] >= 2
    assert any(x['building']=='1号楼' for x in a.ok('/admin/dormitories?page=2')['data']['rows'])
    print('PASS 账号、学生、宿舍、床位增删改查及分页')
    s.fail('/repairs','POST',{'description':'缺少位置'},'请选择报修宿舍')
    s.ok('/repairs','POST',{'description':'测试漏水','dormitory_id':1})
    rid=next(x['id'] for x in s.ok('/repairs')['rows'] if x['description']=='测试漏水')
    s.ok(f'/repairs/{rid}','PUT',{'description':'测试漏水已修改','dormitory_id':1})
    s.fail(f'/repairs/{rid}/accept','POST',{},'仅管理员')
    a.fail(f'/repairs/{rid}/complete','POST',{},'状态已变化')
    a.ok(f'/repairs/{rid}/accept','POST',{})
    a.fail(f'/repairs/{rid}/complete','POST',{},'完成说明不能为空')
    a.ok(f'/repairs/{rid}/complete','POST',{'completion_note':'已更换水管'})
    s.fail(f'/repairs/{rid}/confirm','POST',{'evaluation':'处理及时'},'请选择满意或不满意')
    s.ok(f'/repairs/{rid}/confirm','POST',{'evaluation':'处理及时','satisfaction':'SATISFIED'})
    s.fail(f'/repairs/{rid}/confirm','POST',{'evaluation':'重复','satisfaction':'DISSATISFIED'},'状态已变化')
    assert s.ok(f'/repairs/{rid}')['completion_note']=='已更换水管'
    assert s.ok(f'/repairs/{rid}')['satisfaction']=='SATISFIED'
    assert next(x for x in s.ok('/repairs')['rows'] if x['id']==rid)['status']=='CONFIRMED'
    assert s.ok(f'/repairs/{rid}')['status']=='CONFIRMED'
    s.ok('/repairs','POST',{'description':'临时待删除','dormitory_id':1})
    temp=next(x['id'] for x in s.ok('/repairs')['rows'] if x['description']=='临时待删除')
    s.ok(f'/repairs/{temp}','DELETE')
    print('PASS 报修增删改查、四状态流转、状态与权限校验')
    a.fail('/housing/1/checkout','POST',{},'状态已变化')
    a.ok('/housing/1/checkin','POST',{}); a.fail('/housing/1/checkout','POST',{},'尚未申请退宿')
    s.ok('/housing/1/request-checkout','POST',{}); a.ok('/housing/1/checkout','POST',{})
    assert next(x for x in s.ok('/repairs')['rows'] if x['id']==rid)['dorm_label']=='1号楼 301'
    a.ok('/admin/dormitories/1','PUT',{'building':'1号楼','room_no':'302','room_type':'6人间'})
    assert next(x for x in s.ok('/repairs')['rows'] if x['id']==rid)['dorm_label']=='1号楼 301'
    assert a.ok('/admin/dormitories/1')['room_type']=='6人间'
    a.ok('/admin/dormitories/1','PUT',{'building':'1号楼','room_no':'301','room_type':'4人间'})
    assert [x['id'] for x in s.ok('/beds/free')]==[1,2]
    s.fail('/housing','POST',{},'请选择空闲床位')
    s.ok('/housing','POST',{'bedId':2}); s.fail('/housing','POST',{'bedId':2},'已有未结束')
    hid=next(x['id'] for x in s.ok('/housing')['rows'] if x['status']=='PENDING')
    record=next(x for x in s.ok('/housing')['rows'] if x['id']==hid)
    assert record['status']=='PENDING' and record['bed_id']==2
    assert any(x['id']==2 and x['status']=='FREE' for x in a.ok('/admin/beds')['data']['rows'])
    a.ok(f'/housing/{hid}/assign','POST',{'bedId':2})
    assert s.ok('/housing/active')['status']=='ASSIGNED'
    s.fail(f'/housing/{hid}/unassign','POST',{},'仅管理员')
    a.ok(f'/housing/{hid}/unassign','POST',{})
    record=next(x for x in s.ok('/housing')['rows'] if x['id']==hid)
    assert record['status']=='PENDING' and record['bed_id'] is None and record['checkout_requested_at'] is None
    assert any(x['id']==2 and x['status']=='FREE' for x in a.ok('/admin/beds')['data']['rows'])
    a.ok(f'/housing/{hid}/assign','POST',{'bedId':2})
    other=Client(); other.login('teststudent02')
    other.fail(f'/housing/{hid}/request-checkout','POST',{},'无权操作其他学生')
    assert [x['id'] for x in other.ok('/beds/free')]==[1]
    other.fail('/housing','POST',{'bedId':2},'已被占用')
    other.ok('/housing','POST',{'bedId':1})
    other_id=next(x['id'] for x in other.ok('/housing')['rows'] if x['status']=='PENDING')
    assert next(x for x in other.ok('/housing')['rows'] if x['id']==other_id)['bed_id']==1
    a.fail(f'/admin/students/{sid}','DELETE',None,'入住记录')
    a.fail(f'/housing/{other_id}/assign','POST',{'bedId':2},'状态已变化')
    a.ok(f'/housing/{hid}/checkin','POST',{})
    a.fail(f'/housing/{hid}/cancel-checkout','POST',{},'仅学生')
    s.ok(f'/housing/{hid}/request-checkout','POST',{})
    s.ok(f'/housing/{hid}/cancel-checkout','POST',{})
    record=next(x for x in s.ok('/housing')['rows'] if x['id']==hid)
    assert record['status']=='CHECKED_IN' and record['checkout_requested_at'] is None
    s.fail(f'/housing/{hid}/force-checkout','POST',{},'仅管理员')
    a.ok(f'/housing/{hid}/force-checkout','POST',{})
    record=next(x for x in s.ok('/housing')['rows'] if x['id']==hid)
    assert record['status']=='CHECKED_OUT' and record['checkout_requested_at'] is None
    assert s.ok('/housing/active')['active'] is False
    a.fail('/admin/beds/2','DELETE',None,'入住记录')
    profile=other.ok('/profile')
    other.fail('/profile','PUT',{'name':profile['name'],'student_no':'CHANGED','password':''},'学号由管理员维护')
    other.fail('/profile','PUT',{'name':profile['name'],'password':'654321','oldPassword':'bad'},'原密码不正确')
    second=Client(); second.login('teststudent02')
    other.ok('/profile','PUT',{'name':profile['name'],'password':'654321','oldPassword':'123456'})
    assert second.call('/repairs')[0] == 401
    assert other.ok('/repairs') is not None
    other.ok('/repairs','POST',{'description':'待删除报修','dormitory_id':1})
    other_repair=next(x['id'] for x in other.ok('/repairs')['rows'] if x['description']=='待删除报修')
    a.fail(f'/admin/students/{sid}','DELETE',None,'报修记录')
    a.ok(f'/repairs/{other_repair}','DELETE')
    a.ok(f'/admin/users/{uid}','PUT',{'username':'teststudent02','password':'abcdef','role':'STUDENT'})
    assert other.call('/repairs')[0] == 401
    admin_id=row(a,'users','username','admin')['id']
    a.fail(f'/admin/users/{admin_id}','PUT',{'username':'admin','password':'abcdef','role':'ADMIN','oldPassword':'bad'},'原密码不正确')
    a.ok(f'/admin/users/{admin_id}','PUT',{'username':'admin','password':'abcdef','role':'ADMIN','oldPassword':'123456'})
    a.ok(f'/admin/users/{admin_id}','PUT',{'username':'admin','password':'123456','role':'ADMIN','oldPassword':'abcdef'})
    a.fail(f'/admin/students/{sid}','DELETE',None,'入住记录')
    a.ok(f'/housing/{other_id}','DELETE'); a.ok(f'/admin/students/{sid}','DELETE'); a.ok(f'/admin/users/{uid}','DELETE')
    beds=a.ok('/admin/beds')['data']['rows']; assert all(x['status']=='FREE' for x in beds)
    stats=a.ok('/admin/statistics'); assert int(stats['beds']['occupied'])==0 and int(stats['beds']['total'])==2
    assert int(stats['repairs']['done'])==1 and int(stats['repairs']['total'])==2
    print('PASS 入住/退宿/取消分配/撤销与强制退宿、改密与删除约束、床位释放与统计')
    print('全部 API 测试通过（测试库中的数据已改变）。')
if __name__=='__main__': main()
