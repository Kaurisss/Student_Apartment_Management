<script setup>
defineProps({ label:{type:String,required:true}, danger:{type:Boolean,default:false} })
defineEmits(['click'])
</script>

<template>
  <a-tooltip :content="label" :trigger="['hover','focus']">
    <button type="button" class="table-icon-button" :class="{danger}" :aria-label="label" @click="$emit('click',$event)">
      <span aria-hidden="true"><slot /></span>
    </button>
  </a-tooltip>
</template>
