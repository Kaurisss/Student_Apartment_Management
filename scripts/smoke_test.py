#!/usr/bin/env python3
"""Exercise the JSON API against a disposable copy of assignment2-original.sql, never a real database.
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
    a,s=Client(),Client(); a.login('admin'); s.login('student01')
    assert a.ok('/session')['admin'] and not s.ok('/session')['admin']
    assert s.call('/admin/users')[0] == 403
    assert a.call('/admin/dormitories','POST',{'building':'x','room_no':'x'},csrf=False)[0] == 403
    print('PASS 登录、角色隔离、CSRF')
    a.ok('/admin/users','POST',{'username':'teststudent02','password':'123456','role':'STUDENT'})
    uid=row(a,'users','username','teststudent02')['id']
    a.ok(f'/admin/users/{uid}','PUT',{'username':'teststudent02','password':'','role':'STUDENT'})
    a.ok('/admin/students','POST',{'user_id':str(uid),'student_no':'TEST002','name':'测试二'})
    sid=row(a,'students','student_no','TEST002')['id']
    a.ok(f'/admin/students/{sid}','PUT',{'user_id':str(uid),'student_no':'TEST002','name':'测试二改'})
    a.ok('/admin/dormitories','POST',{'building':'测试楼','room_no':'999'})
    did=row(a,'dormitories','building','测试楼')['id']
    a.ok(f'/admin/dormitories/{did}','PUT',{'building':'测试楼','room_no':'998'})
    a.ok('/admin/beds','POST',{'dormitory_id':str(did),'bed_no':'T1'})
    bid=row(a,'beds','bed_no','T1')['id']
    a.ok(f'/admin/beds/{bid}','PUT',{'dormitory_id':str(did),'bed_no':'T2'})
    a.fail(f'/admin/dormitories/{did}','DELETE',None,'关联')
    a.ok(f'/admin/beds/{bid}','DELETE'); a.ok(f'/admin/dormitories/{did}','DELETE')
    for i in range(11): a.ok('/admin/dormitories','POST',{'building':'分页楼','room_no':f'{i:03d}'})
    assert a.ok('/admin/dormitories')['data']['pages'] >= 2
    assert any(x['building']=='1号楼' for x in a.ok('/admin/dormitories?page=2')['data']['rows'])
    print('PASS 账号、学生、宿舍、床位增删改查及分页')
    s.ok('/repairs','POST',{'description':'测试漏水'})
    rid=next(x['id'] for x in s.ok('/repairs')['rows'] if x['description']=='测试漏水')
    s.ok(f'/repairs/{rid}','PUT',{'description':'测试漏水已修改'})
    s.fail(f'/repairs/{rid}/accept','POST',{},'仅管理员')
    a.fail(f'/repairs/{rid}/complete','POST',{},'状态已变化')
    a.ok(f'/repairs/{rid}/accept','POST',{}); a.ok(f'/repairs/{rid}/complete','POST',{})
    s.ok(f'/repairs/{rid}/confirm','POST',{'evaluation':'处理及时'})
    assert next(x for x in s.ok('/repairs')['rows'] if x['id']==rid)['status']=='CONFIRMED'
    s.ok('/repairs','POST',{'description':'临时待删除'})
    temp=next(x['id'] for x in s.ok('/repairs')['rows'] if x['description']=='临时待删除')
    s.ok(f'/repairs/{temp}','DELETE')
    print('PASS 报修增删改查、四状态流转、状态与权限校验')
    a.fail('/housing/1/checkout','POST',{},'状态已变化')
    a.ok('/housing/1/checkin','POST',{}); a.fail('/housing/1/checkout','POST',{},'尚未申请退宿')
    s.ok('/housing/1/request-checkout','POST',{}); a.ok('/housing/1/checkout','POST',{})
    s.ok('/housing','POST',{}); s.fail('/housing','POST',{},'已有未结束')
    hid=next(x['id'] for x in s.ok('/housing')['rows'] if x['status']=='PENDING')
    a.ok(f'/housing/{hid}/assign','POST',{'bedId':2})
    other=Client(); other.login('teststudent02')
    other.fail(f'/housing/{hid}/request-checkout','POST',{},'无权操作其他学生')
    other.ok('/housing','POST',{})
    other_id=next(x['id'] for x in other.ok('/housing')['rows'] if x['status']=='PENDING')
    a.fail(f'/housing/{other_id}/assign','POST',{'bedId':2},'状态已变化')
    a.ok(f'/housing/{other_id}','DELETE'); a.ok(f'/admin/students/{sid}','DELETE'); a.ok(f'/admin/users/{uid}','DELETE')
    a.ok(f'/housing/{hid}/checkin','POST',{}); s.ok(f'/housing/{hid}/request-checkout','POST',{}); a.ok(f'/housing/{hid}/checkout','POST',{})
    assert next(x for x in s.ok('/housing')['rows'] if x['id']==hid)['status']=='CHECKED_OUT'
    beds=a.ok('/admin/beds')['data']['rows']; assert all(x['status']=='FREE' for x in beds)
    stats=a.ok('/admin/statistics'); assert int(stats['beds']['occupied'])==0 and int(stats['beds']['total'])==2
    assert int(stats['repairs']['done'])==1 and int(stats['repairs']['total'])==2
    print('PASS 入住/退宿、同床位冲突、跨用户越权、床位释放与统计')
    print('全部 API 测试通过（测试库中的数据已改变）。')
if __name__=='__main__': main()
