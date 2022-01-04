variable "project_tag" {
  default = "4.3"
}

variable "emr_cluster_name" {
  default = "p43-emr-cluster"
}

variable "region" {
  default = "us-east-1"
}

variable "zone" {
  default = "us-east-1b"
}

variable "cluster_release_label" {
  default = "emr-5.13.0"
}

variable "master_node_instance_type" {
  default = "m4.large"
}

variable "master_node_instance_count" {
  default = "1"
}

variable "master_node_bid_price" {
  default = "0.1"
}

variable "core_node_instance_type" {
  default = "m4.large"
}

variable "core_node_instance_count" {
  default = "2"
}

variable "core_node_bid_price" {
  default = "0.1"
}

# Update "key_name" with the key pair name
variable "key_name" {
  default = "project"
}

# Update "config" with the json file which defines EMR configurations
# You can update the configurations in the emr_configuration.json file
variable "config" {
  default = "emr_configurations.json"
}

variable "workspace_instance_type" {
  default = "t3.micro"
}

variable "workspace_ami" {
  default = "ami-0795e993eb27953cf"
}
